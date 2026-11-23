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
import model.tile.FreeParkingTile;
import model.tile.GoTile;
import model.tile.GoToJailTile;
import model.tile.JailTile;
import model.tile.PropertyTile;
import model.tile.RailroadProperty;
import model.tile.StreetProperty;
import model.tile.TaxTile;
import model.tile.Tile;
import model.tile.UtilityProperty;
import network.GameMessage;

import java.util.List;
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

        String phaseName = engine.getCurrentPhase().name();
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
                    result.getCode(), d1, d2, total, pos, money, phaseName, room.getTurnId()
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
        }

        ActionResult result = engine.buyProperty(roomId, playerId, turnId, "BUY-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        String phaseName = engine.getCurrentPhase().name();
        synchronized (room) {
            Player currentPlayer = room.getCurrentPlayer();
            if (currentPlayer == null) {
                return buildError(roomId, playerId, turnId, "PLAYER_NOT_FOUND", "Không tìm thấy người chơi hiện tại.");
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
                    phaseName
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

        TurnPhase enginePhase = engine.getCurrentPhase();
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
            if (!isPhaseCompatible(enginePhase, item.getTriggerPhase())) {
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
                    enginePhase.name()
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

        String phaseName = engine.getCurrentPhase().name();
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
                    phaseName,
                    room.getStatus().name()
            );
            return new GameMessage("TURN_CHANGED", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Endpoint: /app/room/{roomId}/pay-bail
     */
    public GameMessage handlePayBail(String roomId, String playerId, int turnId, GameEngine engine) {
        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }

        TurnPhase enginePhase = engine.getCurrentPhase();
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
            if (currentPlayer.getStatus() != PlayerStatus.IN_JAIL) {
                return buildError(roomId, playerId, turnId, "NOT_IN_JAIL", "Người chơi không đang ở trong tù.");
            }
            if (enginePhase != TurnPhase.AWAITING_ROLL) {
                return buildError(roomId, playerId, turnId, "INVALID_PHASE", "Chỉ có thể nộp bảo lãnh trước khi gieo xúc xắc.");
            }

            boolean paid = currentPlayer.payBail();
            if (!paid) {
                return buildError(roomId, playerId, turnId, "NOT_ENOUGH_MONEY", "Không đủ $50 để nộp tiền bảo lãnh.");
            }

            String payload = String.format(
                    "{\"playerId\":\"%s\",\"money\":%d,\"status\":\"%s\",\"turnId\":%d,\"phase\":\"%s\"}",
                    escape(playerId),
                    currentPlayer.getMoney(),
                    currentPlayer.getStatus().name(),
                    room.getTurnId(),
                    enginePhase.name()
            );
            return new GameMessage("BAIL_PAID", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Tạo bản chụp trạng thái phòng chơi (Snapshot DTO JSON) để đồng bộ cho toàn bộ Client hoặc khi Reconnect.
     */
    public GameMessage buildSnapshotMessage(GameEngine engine) {
        return buildSnapshotMessage(engine, null);
    }

    /**
     * Tạo bản chụp trạng thái phòng chơi kèm thông tin hostId đầy đủ cho client.
     */
    public GameMessage buildSnapshotMessage(GameEngine engine, String hostId) {
        return buildSnapshotMessage(engine, hostId, 0L, List.of());
    }

    /**
     * Tạo bản chụp trạng thái phòng chơi đầy đủ kèm deadline lượt chơi từ Server và nhật ký ván đấu gần nhất.
     */
    public GameMessage buildSnapshotMessage(
            GameEngine engine,
            String hostId,
            long turnDeadlineEpochMs,
            List<String> recentLogs) {
        if (engine == null) {
            return buildError(null, "SERVER", 0, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }
        String phaseName = engine.getCurrentPhase().name();
        GameRoom room = engine.getRoom();
        synchronized (room) {
            String resolvedHostId = hostId;
            if ((resolvedHostId == null || resolvedHostId.isBlank()) && !room.getPlayers().isEmpty()) {
                resolvedHostId = room.getPlayers().get(0).getId();
            }

            StringBuilder playersJson = new StringBuilder("[");
            List<Player> players = room.getPlayers();
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                if (i > 0) playersJson.append(",");

                StringBuilder ownedTilesJson = new StringBuilder("[");
                List<PropertyTile> ownedProps = p.getOwnedProperties();
                for (int j = 0; j < ownedProps.size(); j++) {
                    if (j > 0) ownedTilesJson.append(",");
                    ownedTilesJson.append(ownedProps.get(j).getIndex());
                }
                ownedTilesJson.append("]");

                StringBuilder itemsJson = new StringBuilder("[");
                Inventory inventory = p.getInventory();
                if (inventory != null && inventory.getItems() != null) {
                    List<Item> items = inventory.getItems();
                    for (int j = 0; j < items.size(); j++) {
                        Item item = items.get(j);
                        if (j > 0) itemsJson.append(",");
                        itemsJson.append(String.format(
                                "{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"triggerPhase\":\"%s\"}",
                                escape(item.getId()),
                                escape(item.getName()),
                                escape(item.getDescription()),
                                item.getTriggerPhase() != null ? item.getTriggerPhase().name() : ""
                        ));
                    }
                }
                itemsJson.append("]");

                playersJson.append(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"money\":%d,\"position\":%d,\"status\":\"%s\",\"ownedCount\":%d,\"isBot\":%b,\"ownedTiles\":%s,\"items\":%s}",
                        escape(p.getId()),
                        escape(p.getName()),
                        p.getMoney(),
                        p.getPosition(),
                        p.getStatus().name(),
                        ownedProps.size(),
                        p.isBot(),
                        ownedTilesJson,
                        itemsJson
                ));
            }
            playersJson.append("]");

            StringBuilder tilesJson = new StringBuilder("[");
            List<Tile> tiles = room.getBoard().getTiles();
            for (int i = 0; i < tiles.size(); i++) {
                Tile tile = tiles.get(i);
                if (i > 0) tilesJson.append(",");
                String tileType = resolveTileType(tile);
                int price = (tile instanceof PropertyTile prop) ? prop.getPrice() : 0;
                String ownerIdJson = "null";
                if (tile instanceof PropertyTile prop && prop.getOwner() != null) {
                    ownerIdJson = "\"" + escape(prop.getOwner().getId()) + "\"";
                }
                tilesJson.append(String.format(
                        "{\"index\":%d,\"name\":\"%s\",\"type\":\"%s\",\"price\":%d,\"ownerId\":%s,\"frozen\":%b}",
                        tile.getIndex(),
                        escape(tile.getName()),
                        tileType,
                        price,
                        ownerIdJson,
                        tile.isFrozen()
                ));
            }
            tilesJson.append("]");

            StringBuilder logsJson = new StringBuilder("[");
            if (recentLogs != null) {
                boolean first = true;
                for (String entry : recentLogs) {
                    if (entry == null) {
                        continue;
                    }
                    if (!first) {
                        logsJson.append(",");
                    }
                    first = false;
                    logsJson.append("\"").append(escape(entry)).append("\"");
                }
            }
            logsJson.append("]");

            Dice lastDice = room.getLastDice();
            String lastDiceJson = (lastDice == null)
                    ? "null"
                    : String.format("{\"dice1\":%d,\"dice2\":%d,\"total\":%d}",
                    lastDice.getDice1(), lastDice.getDice2(), lastDice.getTotal());

            long serverTimeEpochMs = System.currentTimeMillis();
            long safeDeadline = Math.max(0L, turnDeadlineEpochMs);

            Player current = room.getCurrentPlayer();
            String payload = String.format(
                    "{\"roomId\":\"%s\",\"roomName\":\"%s\",\"hostId\":\"%s\",\"roomStatus\":\"%s\",\"maxPlayers\":%d,\"turnId\":%d,\"phase\":\"%s\",\"currentPlayerId\":\"%s\",\"turnDeadlineEpochMs\":%d,\"serverTimeEpochMs\":%d,\"lastDice\":%s,\"players\":%s,\"tiles\":%s,\"logs\":%s}",
                    escape(room.getId()),
                    escape(room.getName()),
                    escape(resolvedHostId == null ? "" : resolvedHostId),
                    room.getStatus().name(),
                    room.getMaxPlayers(),
                    room.getTurnId(),
                    phaseName,
                    escape(current != null ? current.getId() : ""),
                    safeDeadline,
                    serverTimeEpochMs,
                    lastDiceJson,
                    playersJson,
                    tilesJson,
                    logsJson
            );
            return new GameMessage("GAME_STATE_SNAPSHOT", "SERVER", room.getId(), room.getTurnId(), payload);
        }
    }

    private static String resolveTileType(Tile tile) {
        if (tile instanceof GoTile) return "GO";
        if (tile instanceof StreetProperty) return "STREET";
        if (tile instanceof RailroadProperty) return "RAILROAD";
        if (tile instanceof UtilityProperty) return "UTILITY";
        if (tile instanceof TaxTile) return "TAX";
        if (tile instanceof JailTile) return "JAIL";
        if (tile instanceof GoToJailTile) return "GO_TO_JAIL";
        if (tile instanceof FreeParkingTile) return "FREE_PARKING";
        return "FREE_PARKING";
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
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}