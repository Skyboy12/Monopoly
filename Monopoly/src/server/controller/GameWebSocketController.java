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
import model.event.GameEvent;
import model.event.GameEventManager;
import model.tile.CardTile;
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
import server.gateway.SimpleJson;
import server.service.RoomManagerService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        GameRoom room = engine.getRoom();
        int fromPosition = 0;
        boolean wasInJail = false;
        synchronized (room) {
            Player prePlayer = findPlayer(room, playerId);
            if (prePlayer != null) {
                fromPosition = prePlayer.getPosition();
                wasInJail = prePlayer.getStatus() == PlayerStatus.IN_JAIL;
            }
        }

        ActionResult result = engine.rollDice(roomId, playerId, turnId, "ROLL-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        String phaseName = engine.getEffectivePhase().name();
        synchronized (room) {
            Dice dice = room.getLastDice();
            Player player = findPlayer(room, playerId);
            int d1 = (dice != null) ? dice.getDice1() : 0;
            int d2 = (dice != null) ? dice.getDice2() : 0;
            int total = (dice != null) ? dice.getTotal() : 0;
            int finalPos = (player != null) ? player.getPosition() : 0;
            int newPos = finalPos;
            if (!"THREE_DOUBLES".equals(result.getCode()) && (!wasInJail || (player != null && player.getStatus() != PlayerStatus.IN_JAIL))) {
                newPos = (fromPosition + total) % 40;
            }
            int money = (player != null) ? player.getMoney() : 0;

            String payload = String.format(
                    "{\"code\":\"%s\",\"playerId\":\"%s\",\"dice1\":%d,\"dice2\":%d,\"total\":%d,\"fromPosition\":%d,\"newPosition\":%d,\"finalPosition\":%d,\"playerMoney\":%d,\"phase\":\"%s\",\"currentTurnId\":%d}",
                    result.getCode(), SimpleJson.escape(playerId), d1, d2, total, fromPosition, newPos, finalPos, money, phaseName, room.getTurnId()
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

        String phaseName = engine.getEffectivePhase().name();
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
                    SimpleJson.escape(property.getName()),
                    property.getPrice(),
                    SimpleJson.escape(playerId),
                    currentPlayer.getMoney(),
                    phaseName
            );
            return new GameMessage("PROPERTY_BOUGHT", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Endpoint: /app/room/{roomId}/sell-property
     */
    public GameMessage handleSellProperty(String roomId, String playerId, int turnId, int tileIndex, GameEngine engine) {
        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }

        ActionResult result = engine.sellProperty(roomId, playerId, turnId, tileIndex, "SELL-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        String phaseName = engine.getEffectivePhase().name();
        synchronized (roomId != null ? engine.getRoom() : engine) {
            GameRoom room = engine.getRoom();
            Player seller = room.getCurrentPlayer();
            if (seller == null || !seller.getId().equals(playerId)) {
                for (Player p : room.getPlayers()) {
                    if (p.getId().equals(playerId)) {
                        seller = p;
                        break;
                    }
                }
            }
            if (seller == null) {
                return buildError(roomId, playerId, turnId, "PLAYER_NOT_FOUND", "Không tìm thấy người chơi.");
            }
            Tile tile = room.getBoard().getTile(tileIndex);
            String tileName = (tile != null && tile.getName() != null) ? tile.getName() : ("Ô #" + tileIndex);
            int refund = 0;
            if (tile instanceof PropertyTile prop) {
                int buildingValue = 0;
                if (prop instanceof StreetProperty sp) {
                    buildingValue = sp.isHasHotel()
                            ? 5 * sp.getBuildingCost()
                            : sp.getHouseCount() * sp.getBuildingCost();
                }
                refund = (prop.getPrice() + buildingValue) / 2;
            }
            int debt = seller.getPendingDebt();
            boolean settled = !seller.hasPendingDebt();

            String payload = String.format(
                    "{\"sellerId\":\"%s\",\"tileIndex\":%d,\"tileName\":\"%s\",\"refund\":%d,\"remainingMoney\":%d,\"debt\":%d,\"settled\":%b,\"phase\":\"%s\"}",
                    SimpleJson.escape(playerId),
                    tileIndex,
                    SimpleJson.escape(tileName),
                    refund,
                    seller.getMoney(),
                    debt,
                    settled,
                    SimpleJson.escape(phaseName)
            );
            return new GameMessage("PROPERTY_SOLD", playerId, room.getId(), room.getTurnId(), payload);
        }
    }

    /**
     * Endpoint: /app/room/{roomId}/upgrade-property
     */
    public GameMessage handleUpgradeProperty(String roomId, String playerId, int turnId, int tileIndex, GameEngine engine) {
        if (engine == null) {
            return buildError(roomId, playerId, turnId, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }

        ActionResult result = engine.upgradeProperty(roomId, playerId, turnId, tileIndex, "UPGRADE-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        String phaseName = engine.getEffectivePhase().name();
        GameRoom room = engine.getRoom();
        synchronized (room) {
            Tile tile = room.getBoard().getTile(tileIndex);
            if (!(tile instanceof StreetProperty street)) {
                return buildError(roomId, playerId, turnId, "NOT_STREET_PROPERTY", "Ô chỉ định không phải đất ở.");
            }
            Player owner = street.getOwner();
            String ownerId = (owner != null) ? owner.getId() : playerId;
            int remainingMoney = (owner != null) ? owner.getMoney() : 0;
            String payload = String.format(
                    "{\"tileIndex\":%d,\"tileName\":\"%s\",\"cost\":%d,\"ownerId\":\"%s\",\"houseCount\":%d,\"hasHotel\":%b,\"remainingMoney\":%d,\"phase\":\"%s\"}",
                    street.getIndex(),
                    SimpleJson.escape(street.getName()),
                    street.getBuildingCost(),
                    SimpleJson.escape(ownerId),
                    street.getHouseCount(),
                    street.isHasHotel(),
                    remainingMoney,
                    phaseName
            );
            return new GameMessage("PROPERTY_UPGRADED", playerId, room.getId(), room.getTurnId(), payload);
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
        String itemName = itemId;
        synchronized (room) {
            Player targetPlayer = findPlayer(room, playerId);
            if (targetPlayer != null && targetPlayer.getInventory() != null) {
                Item found = targetPlayer.getInventory().getItemById(itemId);
                if (found != null && found.getName() != null && !found.getName().isBlank()) {
                    itemName = found.getName();
                }
            }
        }

        ItemTargetContext safeContext = (context != null) ? context : new ItemTargetContext();
        ActionResult result = engine.useItem(roomId, playerId, turnId, itemId, safeContext, "ITEM-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        String phaseName = engine.getEffectivePhase().name();
        synchronized (room) {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("itemId", itemId);
            payloadMap.put("itemName", itemName);
            payloadMap.put("actorId", playerId);
            payloadMap.put("auto", false);
            payloadMap.put("targetPlayerId", safeContext.getTargetPlayerId());
            payloadMap.put("targetTileIndex", safeContext.getTargetTileIndex());
            payloadMap.put("desiredDiceValue", safeContext.getDesiredDiceValue());
            payloadMap.put("tileIndex", null);
            payloadMap.put("bot", false);
            payloadMap.put("phase", phaseName);
            return new GameMessage("ITEM_USED", playerId, room.getId(), room.getTurnId(), SimpleJson.stringify(payloadMap));
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

        String phaseName = engine.getEffectivePhase().name();
        GameRoom room = engine.getRoom();
        synchronized (room) {
            Player nextPlayer = room.getCurrentPlayer();
            String nextPlayerId = (nextPlayer != null) ? nextPlayer.getId() : "";
            String payload = String.format(
                    "{\"code\":\"%s\",\"previousPlayerId\":\"%s\",\"currentPlayerId\":\"%s\",\"newTurnId\":%d,\"phase\":\"%s\",\"roomStatus\":\"%s\"}",
                    result.getCode(),
                    SimpleJson.escape(playerId),
                    SimpleJson.escape(nextPlayerId),
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

        ActionResult result = engine.payBail(roomId, playerId, turnId, "BAIL-" + UUID.randomUUID());
        if (!result.isSuccess()) {
            return buildError(roomId, playerId, turnId, result.getCode(), result.getMessage());
        }

        String phaseName = engine.getEffectivePhase().name();
        GameRoom room = engine.getRoom();
        synchronized (room) {
            Player currentPlayer = findPlayer(room, playerId);
            int money = (currentPlayer != null) ? currentPlayer.getMoney() : 0;
            String status = (currentPlayer != null) ? currentPlayer.getStatus().name() : PlayerStatus.ACTIVE.name();
            String payload = String.format(
                    "{\"playerId\":\"%s\",\"amount\":50000,\"money\":%d,\"status\":\"%s\",\"turnId\":%d,\"phase\":\"%s\"}",
                    SimpleJson.escape(playerId),
                    money,
                    status,
                    room.getTurnId(),
                    phaseName
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
        return buildSnapshotMessage(engine, hostId, turnDeadlineEpochMs, recentLogs, 30);
    }

    public GameMessage buildSnapshotMessage(
            GameEngine engine,
            String hostId,
            long turnDeadlineEpochMs,
            List<String> recentLogs,
            int turnTimeoutSeconds) {
        return buildSnapshotMessage(engine, hostId, turnDeadlineEpochMs, recentLogs, turnTimeoutSeconds, List.of());
    }

    public GameMessage buildSnapshotMessage(
            GameEngine engine,
            String hostId,
            long turnDeadlineEpochMs,
            List<String> recentLogs,
            int turnTimeoutSeconds,
            List<RoomManagerService.RoomLogEntry> timelineEntries) {
        if (engine == null) {
            return buildError(null, "SERVER", 0, "ENGINE_NOT_FOUND", "Không tìm thấy GameEngine.");
        }
        String phaseName = engine.getCurrentPhase().name();
        String actionPhaseName = engine.getEffectivePhase().name();
        boolean botControl = engine.isBotControlActive();
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
                                SimpleJson.escape(item.getId()),
                                SimpleJson.escape(item.getName()),
                                SimpleJson.escape(item.getDescription()),
                                item.getTriggerPhase() != null ? item.getTriggerPhase().name() : ""
                        ));
                    }
                }
                itemsJson.append("]");

                String debtCreditorIdJson = (p.getPendingCreditor() != null)
                        ? "\"" + SimpleJson.escape(p.getPendingCreditor().getId()) + "\""
                        : "null";
                playersJson.append(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"money\":%d,\"position\":%d,\"status\":\"%s\",\"turnsInJail\":%d,\"rentShieldActive\":%b,\"ownedCount\":%d,\"isBot\":%b,\"debt\":%d,\"debtCreditorId\":%s,\"ownedTiles\":%s,\"items\":%s}",
                        SimpleJson.escape(p.getId()),
                        SimpleJson.escape(p.getName()),
                        p.getMoney(),
                        p.getPosition(),
                        p.getStatus().name(),
                        p.getTurnsInJail(),
                        p.isRentShieldActive(),
                        ownedProps.size(),
                        p.isBot(),
                        p.getPendingDebt(),
                        debtCreditorIdJson,
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
                    ownerIdJson = "\"" + SimpleJson.escape(prop.getOwner().getId()) + "\"";
                }
                boolean mortgaged = (tile instanceof PropertyTile prop) && prop.isMortgaged();
                int houseCount = (tile instanceof StreetProperty sp) ? sp.getHouseCount() : 0;
                boolean hasHotel = (tile instanceof StreetProperty sp) && sp.isHasHotel();
                int buildingCost = (tile instanceof StreetProperty sp) ? sp.getBuildingCost() : 0;
                String colorGroupJson = (tile instanceof StreetProperty sp && sp.getColorGroup() != null)
                        ? "\"" + SimpleJson.escape(sp.getColorGroup()) + "\""
                        : "null";
                int rent = 0;
                if (tile instanceof PropertyTile prop
                        && prop.getOwner() != null
                        && !prop.isMortgaged()
                        && !tile.isFrozen()) {
                    rent = prop.calculateRent(room) * Math.max(1, room.getRentMultiplier());
                }
                int baseRent = 0;
                String rentTableJson = "[]";
                if (tile instanceof StreetProperty sp) {
                    baseRent = sp.getBaseRent();
                    int[] rt = sp.getRentTable();
                    StringBuilder rtb = new StringBuilder("[");
                    for (int r = 0; r < rt.length; r++) {
                        if (r > 0) rtb.append(",");
                        rtb.append(rt[r]);
                    }
                    rtb.append("]");
                    rentTableJson = rtb.toString();
                } else if (tile instanceof RailroadProperty) {
                    baseRent = RailroadProperty.BASE_RENT;
                    rentTableJson = "[" + RailroadProperty.rentForCount(1) + ","
                            + RailroadProperty.rentForCount(2) + ","
                            + RailroadProperty.rentForCount(3) + ","
                            + RailroadProperty.rentForCount(4) + "]";
                } else if (tile instanceof UtilityProperty) {
                    baseRent = UtilityProperty.SINGLE_MULTIPLIER;
                    rentTableJson = "[" + UtilityProperty.SINGLE_MULTIPLIER + ","
                            + UtilityProperty.DOUBLE_MULTIPLIER + "]";
                }
                int taxAmount = (tile instanceof TaxTile tax) ? tax.getTaxAmount() : 0;

                tilesJson.append(String.format(
                        "{\"index\":%d,\"name\":\"%s\",\"type\":\"%s\",\"price\":%d,\"ownerId\":%s,\"frozen\":%b,\"houseCount\":%d,\"hasHotel\":%b,\"mortgaged\":%b,\"buildingCost\":%d,\"colorGroup\":%s,\"rent\":%d,\"baseRent\":%d,\"rentTable\":%s,\"taxAmount\":%d}",
                        tile.getIndex(),
                        SimpleJson.escape(tile.getName()),
                        tileType,
                        price,
                        ownerIdJson,
                        tile.isFrozen(),
                        houseCount,
                        hasHotel,
                        mortgaged,
                        buildingCost,
                        colorGroupJson,
                        rent,
                        baseRent,
                        rentTableJson,
                        taxAmount
                ));
            }
            tilesJson.append("]");

            StringBuilder activeEventsJson = new StringBuilder("[");
            GameEventManager eventManager = room.getEventManager();
            if (eventManager != null) {
                List<GameEvent> activeEvents = eventManager.getActiveEvents();
                for (int i = 0; i < activeEvents.size(); i++) {
                    GameEvent ev = activeEvents.get(i);
                    if (i > 0) activeEventsJson.append(",");
                    activeEventsJson.append(String.format(
                            "{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"remainingTurns\":%d}",
                            SimpleJson.escape(ev.getId()),
                            SimpleJson.escape(ev.getName()),
                            SimpleJson.escape(ev.getDescription()),
                            eventManager.getRemainingTurns(ev)
                    ));
                }
            }
            activeEventsJson.append("]");

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
                    logsJson.append("\"").append(SimpleJson.escape(entry)).append("\"");
                }
            }
            logsJson.append("]");

            StringBuilder timelineJson = new StringBuilder("[");
            if (timelineEntries != null) {
                boolean first = true;
                for (RoomManagerService.RoomLogEntry entry : timelineEntries) {
                    if (entry == null) {
                        continue;
                    }
                    if (!first) {
                        timelineJson.append(",");
                    }
                    first = false;
                    timelineJson.append(String.format(
                            "{\"id\":%d,\"ts\":%d,\"message\":\"%s\"}",
                            entry.id(),
                            entry.ts(),
                            SimpleJson.escape(entry.message())
                    ));
                }
            }
            timelineJson.append("]");

            Dice lastDice = room.getLastDice();
            String lastDiceJson = (lastDice == null)
                    ? "null"
                    : String.format("{\"dice1\":%d,\"dice2\":%d,\"total\":%d}",
                    lastDice.getDice1(), lastDice.getDice2(), lastDice.getTotal());

            long serverTimeEpochMs = System.currentTimeMillis();
            long safeDeadline = Math.max(0L, turnDeadlineEpochMs);

            Integer forcedDice = room.getForcedDiceValue();
            String forcedDiceJson = (forcedDice == null) ? "null" : String.valueOf(forcedDice);
            int upgradeTileIndex = engine.getUpgradeableTileIndex();
            boolean upgradedThisTurn = engine.isUpgradedThisTurn();

            Player current = room.getCurrentPlayer();
            String payload = String.format(
                    "{\"roomId\":\"%s\",\"roomName\":\"%s\",\"hostId\":\"%s\",\"roomStatus\":\"%s\",\"maxPlayers\":%d,\"turnId\":%d,\"phase\":\"%s\",\"actionPhase\":\"%s\",\"botControl\":%b,\"currentPlayerId\":\"%s\",\"turnDeadlineEpochMs\":%d,\"serverTimeEpochMs\":%d,\"turnTimeoutSeconds\":%d,\"rentMultiplier\":%d,\"forcedDiceValue\":%s,\"upgradeTileIndex\":%d,\"upgradedThisTurn\":%b,\"activeEvents\":%s,\"lastDice\":%s,\"players\":%s,\"tiles\":%s,\"logs\":%s,\"timeline\":%s}",
                    SimpleJson.escape(room.getId()),
                    SimpleJson.escape(room.getName()),
                    SimpleJson.escape(resolvedHostId == null ? "" : resolvedHostId),
                    room.getStatus().name(),
                    room.getMaxPlayers(),
                    room.getTurnId(),
                    phaseName,
                    actionPhaseName,
                    botControl,
                    SimpleJson.escape(current != null ? current.getId() : ""),
                    safeDeadline,
                    serverTimeEpochMs,
                    Math.max(1, turnTimeoutSeconds),
                    room.getRentMultiplier(),
                    forcedDiceJson,
                    upgradeTileIndex,
                    upgradedThisTurn,
                    activeEventsJson,
                    lastDiceJson,
                    playersJson,
                    tilesJson,
                    logsJson,
                    timelineJson
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
        if (tile instanceof CardTile cardTile) {
            return cardTile.getDeckType() == CardTile.DeckType.CHANCE ? "CHANCE" : "COMMUNITY_CHEST";
        }
        if (tile instanceof FreeParkingTile) return "FREE_PARKING";
        return "FREE_PARKING";
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
        String payload = String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                SimpleJson.escape(errorCode), SimpleJson.escape(message));
        return new GameMessage("ERROR", playerId, roomId, turnId, payload);
    }
}