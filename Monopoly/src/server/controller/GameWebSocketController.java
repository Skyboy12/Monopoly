package server.controller;

import engine.GameEngine;
import engine.GameEngine.ActionResult;
import engine.TurnPhase;
import model.Dice;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.item.Inventory;
import model.item.Item;
import model.item.ItemTargetContext;
import model.item.ItemTriggerPhase;
import model.tile.PropertyTile;
import model.tile.Tile;
import network.GameMessage;

import java.util.UUID;

/**
 * Controller tiếp nhận và xử lý các WebSocket / STOMP command từ Client.
 * Server Authoritative: Mọi thay đổi trạng thái ủy quyền qua GameEngine và Domain.
 */
public class GameWebSocketController {

    /**
     * Endpoint: /app/room/{roomId}/roll-dice
     */
    public GameMessage handleRollDice(String roomId, String playerId, int turnId, GameEngine engine) {
        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }

        ActionResult result = engine.rollDice(roomId, playerId, turnId, "ROLL-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        GameRoom room = engine.getRoom();
        synchronized (room) {
            Dice dice = room.getLastDice();
            Player player = findPlayer(room, playerId);
            int d1 = (dice != null) ? dice.getDice1() : 0;
            int d2 = (dice != null) ? dice.getDice2() : 0;
            int total = (dice != null) ? dice.getTotal() : 0;
            int pos = (player != null) ? player.getPosition() : 0;
            int money = (player != null) ? player.getMoney() : 0;

            String payload = String.format(
                    "{\"code\":\"%s\",\"dice1\":%d,\"dice2\":%d,\"total\":%d,\"newPosition\":%d,\"playerMoney\":%d,\"phase\":\"%s\",\"currentTurnId\":%d}",
                    result.getCode(), d1, d2, total, pos, money, engine.getCurrentPhase().name(), room.getTurnId()
            );
            return new GameMessage("DICE_ROLLED", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Endpoint: /app/room/{roomId}/buy-property
     */
    public GameMessage handleBuyProperty(String roomId, String playerId, int turnId, int tileIndex, GameEngine engine) {
        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }

        GameRoom room = engine.getRoom();
        synchronized (room) {
            Player currentPlayer = room.getCurrentPlayer();
            if (currentPlayer != null && currentPlayer.getId().equals(playerId)) {
                if (currentPlayer.getPosition() != tileIndex) {
                    return buildError(roomId, playerId, turnId,
                            "TILE_MISMATCH", "Vị trí ô đất yêu cầu mua không khớp vị trí hiện tại của người chơi trên Server.");
                }
            }

            ActionResult result = engine.buyProperty(roomId, playerId, turnId, "BUY-" + UUID.randomUUID());
            if (!result.isSuccess()) {
                return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
            }

            Tile tile = room.getBoard().getTile(currentPlayer.getPosition());
            PropertyTile property = (PropertyTile) tile;

            String payload = String.format(
                    "{\"tileIndex\":%d,\"tileName\":\"%s\",\"price\":%d,\"ownerId\":\"%s\",\"remainingMoney\":%d,\"phase\":\"%s\"}",
                    property.getIndex(),
                    escape(property.getName()),
                    property.getPrice(),
                    escape(playerId),
                    currentPlayer.getMoney(),
                    engine.getCurrentPhase().name()
            );
            return new GameMessage("PROPERTY_BOUGHT", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Endpoint: /app/room/{roomId}/use-item
     */
    public GameMessage handleUseItem(String roomId, String playerId, int turnId, String itemId, GameEngine engine) {
        return handleUseItemWithContext(roomId, playerId, turnId, itemId, new ItemTargetContext(), engine);
    }

    /**
     * Hỗ trợ truyền thêm ItemTargetContext khi dùng vật phẩm có mục tiêu (Dynamite, ArrestWarrant, MagicDice).
     */
    public GameMessage handleUseItemWithContext(
            String roomId,
            String playerId,
            int turnId,
            String itemId,
            ItemTargetContext context,
            GameEngine engine) {

        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }
        if (itemId == null || itemId.isBlank()) {
            return buildError(roomId, playerId, turnId, "INVALID_ITEM_ID", "itemId không được để trống.");
        }

        GameRoom room = engine.getRoom();
        synchronized (room) {
            if (!room.getId().equals(roomId)) {
                return buildError(roomId, playerId, turnId, "INVALID_ROOM", "roomId không hợp lệ.");
            }
            if (room.getStatus() != RoomStatus.PLAYING) {
                return buildError(roomId, playerId, turnId, "ROOM_NOT_PLAYING", "Phòng chưa bắt đầu hoặc đã kết thúc.");
            }
            if (room.getTurnId() != turnId) {
                return buildError(roomId, playerId, turnId, "STALE_TURN", "turnId không khớp với lượt hiện tại.");
            }

            Player currentPlayer = room.getCurrentPlayer();
            if (currentPlayer == null || !currentPlayer.getId().equals(playerId)) {
                return buildError(roomId, playerId, turnId, "NOT_YOUR_TURN", "Không phải lượt của người chơi này.");
            }
            if (currentPlayer.getStatus() == PlayerStatus.BANKRUPT) {
                return buildError(roomId, playerId, turnId, "PLAYER_BANKRUPT", "Người chơi đã phá sản.");
            }

            Inventory inventory = currentPlayer.getInventory();
            if (inventory == null || !inventory.hasItem(itemId)) {
                return buildError(roomId, playerId, turnId, "ITEM_NOT_FOUND", "Người chơi không sở hữu vật phẩm này.");
            }

            Item item = inventory.getItemById(itemId);
            if (!isPhaseCompatible(engine.getCurrentPhase(), item.getTriggerPhase())) {
                return buildError(roomId, playerId, turnId, "INVALID_ITEM_PHASE", "Không thể dùng vật phẩm ở giai đoạn này.");
            }

            ItemTargetContext safeContext = (context != null) ? context : new ItemTargetContext();
            if (!item.canUse(currentPlayer, room, safeContext)) {
                return buildError(roomId, playerId, turnId, "ITEM_CONDITION_NOT_MET", "Chưa thỏa mãn điều kiện dùng vật phẩm.");
            }

            boolean applied = item.applyEffect(currentPlayer, room, safeContext);
            if (!applied) {
                return buildError(roomId, playerId, turnId, "ITEM_EFFECT_FAILED", "Kích hoạt hiệu ứng vật phẩm thất bại.");
            }

            inventory.removeItem(itemId);

            String payload = String.format(
                    "{\"itemId\":\"%s\",\"itemName\":\"%s\",\"actorId\":\"%s\",\"phase\":\"%s\"}",
                    escape(item.getId()),
                    escape(item.getName()),
                    escape(playerId),
                    engine.getCurrentPhase().name()
            );
            return new GameMessage("ITEM_USED", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Endpoint: /app/room/{roomId}/end-turn
     */
    public GameMessage handleEndTurn(String roomId, String playerId, int turnId, GameEngine engine) {
        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }

        ActionResult result = engine.endTurn(roomId, playerId, turnId, "END-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        GameRoom room = engine.getRoom();
        synchronized (room) {
            Player nextPlayer = room.getCurrentPlayer();
            String nextPlayerId = (nextPlayer != null) ? nextPlayer.getId() : "";
            String payload = String.format(
                    "{\"code\":\"%s\",\"previousPlayerId\":\"%s\",\"currentPlayerId\":\"%s\",\"newTurnId\":%d,\"phase\":\"%s\",\"roomStatus\":\"%s\"}",
                    result.getCode(),
                    escape(playerId),
                    escape(nextPlayerId),
                    room.getTurnId(),
                    engine.getCurrentPhase().name(),
                    room.getStatus().name()
            );
            return new GameMessage("TURN_CHANGED", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Tạo bản chụp trạng thái phòng chơi (Snapshot DTO JSON) để đồng bộ cho toàn bộ Client hoặc khi Reconnect.
     */
    public GameMessage buildSnapshotMessage(GameEngine engine) {
        GameRoom room = engine.getRoom();
        synchronized (room) {
            StringBuilder playersJson = new StringBuilder("[");
            for (int i = 0; i < room.getPlayers().size(); i++) {
                Player p = room.getPlayers().get(i);
                if (i > 0) playersJson.append(",");
                playersJson.append(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"money\":%d,\"position\":%d,\"status\":\"%s\",\"ownedCount\":%d}",
                        escape(p.getId()), escape(p.getName()), p.getMoney(),
                        p.getPosition(), p.getStatus().name(), p.getOwnedProperties().size()
                ));
            }
            playersJson.append("]");

            Player current = room.getCurrentPlayer();
            String payload = String.format(
                    "{\"roomId\":\"%s\",\"roomStatus\":\"%s\",\"turnId\":%d,\"phase\":\"%s\",\"currentPlayerId\":\"%s\",\"players\":%s}",
                    escape(room.getId()),
                    room.getStatus().name(),
                    room.getTurnId(),
                    engine.getCurrentPhase().name(),
                    escape(current != null ? current.getId() : ""),
                    playersJson
            );
            return new GameMessage("GAME_STATE_SNAPSHOT", "SERVER", room.getId(), room.getTurnId(), payload);
        }
    }

    private boolean isPhaseCompatible(TurnPhase enginePhase, ItemTriggerPhase itemPhase) {
        if (itemPhase == null) return false;
        if (itemPhase == ItemTriggerPhase.PRE_ROLL) {
            return enginePhase == TurnPhase.AWAITING_ROLL;
        }
        if (itemPhase == ItemTriggerPhase.ON_LAND) {
            return enginePhase == TurnPhase.RESOLVING_ACTION || enginePhase == TurnPhase.AWAITING_DECISION;
        }
        if (itemPhase == ItemTriggerPhase.AWAITING_DECISION) {
            return enginePhase == TurnPhase.AWAITING_DECISION;
        }
        return false;
    }

    private Player findPlayer(GameRoom room, String playerId) {
        for (Player p : room.getPlayers()) {
            if (p.getId().equals(playerId)) {
                return p;
            }
        }
        return null;
    }

    private GameMessage buildError(String roomId, String playerId, int turnId, String errorCode, String message) {
        String payload = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", escape(errorCode), escape(message));
        return new GameMessage("ERROR", playerId, roomId, turnId, payload);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}