package engine;

import model.Dice;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.event.GameEvent;
import model.event.GameEventManager;
import model.item.Item;
import model.item.ItemFactory;
import model.item.ItemTargetContext;
import model.item.ItemTriggerPhase;
import model.tile.PropertyTile;
import model.tile.StreetProperty;
import model.tile.Tile;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Game Engine Authoritative chịu trách nhiệm điều phối luật chơi,
 * validate lượt đi và thực hiện hành động cờ tỷ phú.
 *
 * Server là nguồn sự thật duy nhất.
 * Mọi command được xử lý tuần tự theo từng GameRoom.
 */
public class GameEngine {

    private final GameRoom room;
    private TurnPhase currentPhase;

    private int consecutiveDoubles;
    private boolean swappedPositionThisTurn = false;

    // Thông tin khi Bot tiếp quản do timeout.
    private boolean botControlActive;
    private String timeoutPlayerId;
    private int timeoutTurnId = -1;
    private TurnPhase phaseBeforeTimeout;

    // Lưu action đã xử lý để chống duplicate.
    private final Map<String, ActionResult> actionResults =
            new LinkedHashMap<>();

    private static final int MAX_ACTIONS = 1000;

    // Danh sách nhận sự kiện từ Engine.
    private final CopyOnWriteArrayList<Consumer<EngineEvent>> listeners =
            new CopyOnWriteArrayList<>();

    public GameEngine(GameRoom room) {
        if (room == null) {
            throw new IllegalArgumentException("GameRoom không được null.");
        }

        this.room = room;
        this.currentPhase = TurnPhase.AWAITING_ROLL;
        this.consecutiveDoubles = 0;
    }

    // ================= GETTER =================

    public synchronized TurnPhase getCurrentPhase() {
        synchronized (room) {
            if (room.getStatus() == RoomStatus.FINISHED) {
                return TurnPhase.FINISHED;
            }

            return currentPhase;
        }
    }

    public synchronized TurnPhase getEffectivePhase() {
        synchronized (room) {
            if (room.getStatus() == RoomStatus.FINISHED) {
                return TurnPhase.FINISHED;
            }
            return getActionPhase();
        }
    }

    public synchronized boolean isBotControlActive() {
        synchronized (room) {
            return botControlActive;
        }
    }

    public synchronized boolean ensureBotTurnResolved(String roomId, int expectedTurnId) {
        synchronized (room) {
            if (validRoom(roomId)
                    && room.getStatus() == RoomStatus.PLAYING
                    && room.getTurnId() == expectedTurnId
                    && (botControlActive
                    || (room.getCurrentPlayer() != null && room.getCurrentPlayer().isBot()))) {
                advanceTurn(room.getCurrentPlayer(), expectedTurnId);
                return true;
            }
            return false;
        }
    }

    public synchronized GameRoom getRoom() {
        return room;
    }

    /**
     * Lấy trạng thái tổng hợp của game.
     *
     * WAITING, PLAYING, FINISHED lấy từ RoomStatus.
     * Các phase còn lại lấy từ TurnPhase.
     */
    public synchronized String getStateName() {
        synchronized (room) {

            if (room.getStatus() == RoomStatus.WAITING) {
                return "WAITING";
            }

            if (room.getStatus() == RoomStatus.FINISHED) {
                return "FINISHED";
            }

            if (currentPhase == TurnPhase.AWAITING_ROLL) {
                return "PLAYING";
            }

            return currentPhase.name();
        }
    }

    // ================= EVENT =================

    public void addEventListener(Consumer<EngineEvent> listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeEventListener(Consumer<EngineEvent> listener) {
        listeners.remove(listener);
    }

    private void emit(EngineEvent.Type type, Player player,
                      int turnId, Map<String, String> data) {

        EngineEvent event = new EngineEvent(
                type,
                room.getId(),
                player == null ? null : player.getId(),
                turnId,
                currentPhase,
                data
        );

        for (Consumer<EngineEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                // Lỗi listener không làm dừng GameEngine.
            }
        }
    }

    // ================= START GAME =================

    /**
     * Bắt đầu ván chơi.
     * Giữ nguyên phương thức ban đầu trong khung.
     */
    public synchronized boolean startGame() {
        return startGame(room.getId(), newId()).isSuccess();
    }

    // Phương thức bổ sung để kiểm tra roomId và actionId.
    public synchronized ActionResult startGame(
            String roomId, String actionId) {

        synchronized (room) {

            ActionResult old = findDuplicate(
                    "START_GAME", roomId, null,
                    room.getTurnId(), actionId
            );

            if (old != null) {
                return old;
            }

            if (!validRoom(roomId)) {
                return save(result(false, "START_GAME", actionId,
                        "INVALID_ROOM", "roomId không hợp lệ.",
                        null, room.getTurnId()));
            }

            if (!validActionId(actionId)) {
                return result(false, "START_GAME", actionId,
                        "INVALID_ACTION_ID", "actionId không hợp lệ.",
                        null, room.getTurnId());
            }

            if (room.getStatus() != RoomStatus.WAITING) {
                return save(result(false, "START_GAME", actionId,
                        "INVALID_STATE", "Phòng không ở trạng thái WAITING.",
                        null, room.getTurnId()));
            }

            if (room.getPlayers().size() < 2) {
                return save(result(false, "START_GAME", actionId,
                        "NOT_ENOUGH_PLAYERS", "Cần ít nhất 2 người chơi.",
                        null, room.getTurnId()));
            }

            room.setStatus(RoomStatus.PLAYING);
            room.setLastDice(null);

            currentPhase = TurnPhase.AWAITING_ROLL;
            consecutiveDoubles = 0;
            swappedPositionThisTurn = false;
            clearBotControl();

            emit(
                    EngineEvent.Type.GAME_STARTED,
                    room.getCurrentPlayer(),
                    room.getTurnId(),
                    Collections.emptyMap()
            );

            return save(result(true, "START_GAME", actionId,
                    "OK", "Ván chơi đã bắt đầu.",
                    null, room.getTurnId()));
        }
    }

    // ================= ROLL DICE =================

    /**
     * Gieo xúc xắc.
     * Giữ nguyên phương thức ban đầu trong khung.
     */
    public synchronized boolean rollDice(String playerId, int turnId) {

        return rollDiceInternal(
                room.getId(),
                playerId,
                turnId,
                newId(),
                false
        ).isSuccess();
    }

    /**
     * Phiên bản có roomId và actionId.
     * Dùng cho command từ server để chống duplicate
     * và cho phép người chơi giành lại quyền từ Bot.
     */
    public synchronized ActionResult rollDice(
            String roomId,
            String playerId,
            int turnId,
            String actionId) {

        return rollDiceInternal(
                roomId, playerId, turnId, actionId, true
        );
    }

    private ActionResult rollDiceInternal(
            String roomId,
            String playerId,
            int turnId,
            String actionId,
            boolean humanAction) {

        synchronized (room) {

            ActionResult old = findDuplicate(
                    "ROLL_DICE", roomId, playerId, turnId, actionId
            );

            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId, "ROLL_DICE", actionId
            );

            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();

            if (humanAction && player.isBot()) {
                return save(result(false, "ROLL_DICE", actionId,
                        "BOT_CONTROLLED",
                        "Người chơi này là Bot.",
                        playerId, turnId));
            }

            if (getActionPhase() != TurnPhase.AWAITING_ROLL) {
                return save(result(false, "ROLL_DICE", actionId,
                        "INVALID_PHASE",
                        "Không thể gieo xúc xắc lúc này.",
                        playerId, turnId));
            }

            if (player.getStatus() != PlayerStatus.ACTIVE
                    && player.getStatus() != PlayerStatus.IN_JAIL) {
                return save(result(false, "ROLL_DICE", actionId,
                        "PLAYER_NOT_ACTIVE",
                        "Người chơi không thể tung xúc xắc ở trạng thái hiện tại.",
                        playerId, turnId));
            }

            if (humanAction) {
                reclaimHumanControl(player, turnId);
            }

            return save(performRoll(
                    player, playerId, turnId, actionId
            ));
        }
    }

    /**
     * Tạo xúc xắc từ server, di chuyển và gọi tile action.
     */
    private ActionResult performRoll(
            Player player,
            String playerId,
            int turnId,
            String actionId) {

        boolean wasInJail = player.getStatus() == PlayerStatus.IN_JAIL;

        Dice dice = new Dice();
        Integer forcedDice = room.getForcedDiceValue();
        if (forcedDice != null && forcedDice >= 2 && forcedDice <= 12) {
            dice.setForcedTotal(forcedDice);
            room.setForcedDiceValue(null);
        } else {
            dice.roll();
        }

        room.setLastDice(dice);

        int predictedNewPos;
        if (wasInJail) {
            boolean willEscape = dice.isDouble() || player.getTurnsInJail() >= 2;
            predictedNewPos = willEscape ? ((player.getPosition() + dice.getTotal()) % 40) : player.getPosition();
        } else {
            boolean threeDoubles = (consecutiveDoubles + (dice.isDouble() ? 1 : 0)) >= 3;
            predictedNewPos = threeDoubles ? 10 : ((player.getPosition() + dice.getTotal()) % 40);
        }

        emit(
                EngineEvent.Type.DICE_ROLLED,
                player,
                turnId,
                Map.of(
                        "dice1", String.valueOf(dice.getDice1()),
                        "dice2", String.valueOf(dice.getDice2()),
                        "total", String.valueOf(dice.getTotal()),
                        "newPosition", String.valueOf(predictedNewPos)
                )
        );

        if (wasInJail) {
            consecutiveDoubles = 0;
            if (dice.isDouble()) {
                player.setStatus(PlayerStatus.ACTIVE);
                player.setTurnsInJail(0);
            } else {
                Tile jailTile = room.getBoard().getTile(10);
                if (jailTile != null) {
                    jailTile.onLand(player, room);
                }
                if (player.getStatus() != PlayerStatus.ACTIVE) {
                    if (checkGameFinished()) {
                        return result(true, "ROLL_DICE", actionId,
                                "GAME_FINISHED", "Ván chơi đã kết thúc.",
                                playerId, turnId);
                    }
                    boolean bankrupt = player.getStatus() == PlayerStatus.BANKRUPT;
                    advanceTurn(player, turnId);
                    return result(true, "ROLL_DICE", actionId,
                            bankrupt ? "BANKRUPT" : "JAIL_STAY",
                            bankrupt ? "Người chơi đã phá sản." : "Không tung được đôi, tiếp tục ở tù.",
                            playerId, turnId);
                }
            }
        } else {
            if (dice.isDouble()) {
                consecutiveDoubles++;
            } else {
                consecutiveDoubles = 0;
            }

            // Gieo đôi ba lần liên tiếp thì vào tù.
            if (consecutiveDoubles >= 3) {

                player.sendToJail(10);
                consecutiveDoubles = 0;

                emit(
                        EngineEvent.Type.PLAYER_SENT_TO_JAIL,
                        player,
                        turnId,
                        Collections.emptyMap()
                );

                advanceTurn(player, turnId);

                return result(true, "ROLL_DICE", actionId,
                        "THREE_DOUBLES",
                        "Gieo đôi ba lần, người chơi vào tù.",
                        playerId, turnId);
            }
        }

        setActionPhase(TurnPhase.RESOLVING_ACTION);

        int oldPosition = player.getPosition();
        // Player.move() tự cộng $200 khi đi qua GO.
        player.move(dice.getTotal());

        if (oldPosition + dice.getTotal() >= 40
                && player.getStatus() == PlayerStatus.ACTIVE
                && player.getInventory() != null) {
            player.getInventory().addItem(ItemFactory.createRandomItem());
        }

        // Gọi trực tiếp phương thức đa hình.
        Tile tile = room.getBoard().getTile(player.getPosition());

        if (tile != null) {
            try {
                tile.onLand(player, room);

                int hops = 0;
                while (tile.isFrozen() && hops < 5 && player.getStatus() == PlayerStatus.ACTIVE) {
                    hops++;
                    player.move(2);
                    Tile slidTile = room.getBoard().getTile(player.getPosition());
                    if (slidTile == null) {
                        break;
                    }
                    slidTile.onLand(player, room);
                    tile = slidTile;
                }

                if (player.getStatus() == PlayerStatus.IN_JAIL) {
                    consecutiveDoubles = 0;
                }

                emit(
                        EngineEvent.Type.TILE_RESOLVED,
                        player,
                        turnId,
                        Map.of(
                                "tileIndex",
                                String.valueOf(tile.getIndex()),
                                "tileName",
                                tile.getName()
                        )
                );

            } catch (RuntimeException e) {

                // Không gieo lại xúc xắc vì người chơi đã di chuyển.
                setActionPhase(TurnPhase.AWAITING_DECISION);

                return result(false, "ROLL_DICE", actionId,
                        "TILE_ACTION_ERROR",
                        "Không thể xử lý hành động của ô cờ.",
                        playerId, turnId);
            }
        }

        if (checkGameFinished()) {
            return result(true, "ROLL_DICE", actionId,
                    "GAME_FINISHED", "Ván chơi đã kết thúc.",
                    playerId, turnId);
        }

        // Người chơi phá sản thì tự động chuyển lượt.
        if (player.getStatus() == PlayerStatus.BANKRUPT) {
            advanceTurn(player, turnId);

            return result(true, "ROLL_DICE", actionId,
                    "BANKRUPT", "Người chơi đã phá sản.",
                    playerId, turnId);
        }

        setActionPhase(TurnPhase.AWAITING_DECISION);

        return result(true, "ROLL_DICE", actionId,
                "OK", "Đã gieo xúc xắc và xử lý ô cờ.",
                playerId, turnId);
    }

    // ================= BUY PROPERTY =================

    /**
     * Phương thức mua tài sản.
     * BotController sử dụng phương thức này.
     */
    public synchronized boolean buyProperty(
            String playerId, int turnId) {

        return buyPropertyInternal(
                room.getId(), playerId, turnId, newId(), false
        ).isSuccess();
    }

    public synchronized ActionResult buyProperty(
            String roomId, String playerId,
            int turnId, String actionId) {

        return buyPropertyInternal(
                roomId, playerId, turnId, actionId, true
        );
    }

    private ActionResult buyPropertyInternal(
            String roomId,
            String playerId,
            int turnId,
            String actionId,
            boolean humanAction) {

        synchronized (room) {

            ActionResult old = findDuplicate(
                    "BUY_PROPERTY", roomId,
                    playerId, turnId, actionId
            );

            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId,
                    "BUY_PROPERTY", actionId
            );

            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();

            if (humanAction && player.isBot()) {
                return save(result(false, "BUY_PROPERTY",
                        actionId, "BOT_CONTROLLED",
                        "Lượt này do Bot điều khiển.",
                        playerId, turnId));
            }

            invalid = validatePurchase(player);

            if (invalid != null) {
                return save(result(false, "BUY_PROPERTY",
                        actionId, invalid.getCode(),
                        invalid.getMessage(),
                        playerId, turnId));
            }

            if (humanAction) {
                reclaimHumanControl(player, turnId);
            }

            Tile tile = room.getBoard().getTile(player.getPosition());
            PropertyTile property = (PropertyTile) tile;

            player.buyProperty(property);

            boolean success = property.getOwner() == player;

            if (success) {
                emit(
                        EngineEvent.Type.PROPERTY_BOUGHT,
                        player,
                        turnId,
                        Map.of(
                                "tile", property.getName(),
                                "price", String.valueOf(property.getPrice())
                        )
                );
            }

            return save(result(success, "BUY_PROPERTY", actionId,
                    success ? "OK" : "PURCHASE_FAILED",
                    success ? "Mua tài sản thành công."
                            : "Mua tài sản thất bại.",
                    playerId, turnId));
        }
    }

    private ActionResult validatePurchase(Player player) {

        if (getActionPhase() != TurnPhase.AWAITING_DECISION
                || player.getStatus() != PlayerStatus.ACTIVE) {

            return result(false, "BUY_PROPERTY", null,
                    "INVALID_PHASE",
                    "Chưa đến giai đoạn quyết định mua đất.",
                    player.getId(), room.getTurnId());
        }

        if (swappedPositionThisTurn) {
            return result(false, "BUY_PROPERTY", null,
                    "CANNOT_BUY_AFTER_SWAP",
                    "Không thể mua đất sau khi hoán đổi vị trí trong lượt này.",
                    player.getId(), room.getTurnId());
        }

        Tile tile = room.getBoard().getTile(player.getPosition());

        if (!(tile instanceof PropertyTile)) {
            return result(false, "BUY_PROPERTY", null,
                    "NOT_PROPERTY",
                    "Ô hiện tại không phải ô tài sản.",
                    player.getId(), room.getTurnId());
        }

        PropertyTile property = (PropertyTile) tile;

        if (property.hasOwner()) {
            return result(false, "BUY_PROPERTY", null,
                    "PROPERTY_OWNED",
                    "Tài sản đã có chủ.",
                    player.getId(), room.getTurnId());
        }

        if (player.getMoney() < property.getPrice()) {
            return result(false, "BUY_PROPERTY", null,
                    "NOT_ENOUGH_MONEY",
                    "Không đủ tiền mua tài sản.",
                    player.getId(), room.getTurnId());
        }

        return null;
    }

    // ================= PAY BAIL =================

    public synchronized boolean payBail(String playerId, int turnId) {
        return payBailInternal(
                room.getId(), playerId, turnId, newId(), false
        ).isSuccess();
    }

    public synchronized ActionResult payBail(
            String roomId, String playerId, int turnId, String actionId) {
        return payBailInternal(
                roomId, playerId, turnId, actionId, true
        );
    }

    private ActionResult payBailInternal(
            String roomId,
            String playerId,
            int turnId,
            String actionId,
            boolean humanAction) {

        synchronized (room) {
            ActionResult old = findDuplicate(
                    "PAY_BAIL", roomId, playerId, turnId, actionId
            );
            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId, "PAY_BAIL", actionId
            );
            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();
            if (humanAction && player.isBot()) {
                return save(result(false, "PAY_BAIL", actionId,
                        "BOT_CONTROLLED", "Lượt này do Bot điều khiển.",
                        playerId, turnId));
            }

            if (getActionPhase() != TurnPhase.AWAITING_ROLL) {
                return save(result(false, "PAY_BAIL", actionId,
                        "INVALID_PHASE", "Chỉ có thể nộp tiền bảo lãnh trước khi gieo xúc xắc.",
                        playerId, turnId));
            }

            if (player.getStatus() != PlayerStatus.IN_JAIL) {
                return save(result(false, "PAY_BAIL", actionId,
                        "PLAYER_NOT_IN_JAIL", "Người chơi không ở trong tù.",
                        playerId, turnId));
            }

            if (humanAction) {
                reclaimHumanControl(player, turnId);
            }

            if (!player.payBail()) {
                return save(result(false, "PAY_BAIL", actionId,
                        "NOT_ENOUGH_MONEY", "Không đủ $50 để nộp tiền bảo lãnh.",
                        playerId, turnId));
            }

            emit(
                    EngineEvent.Type.BAIL_PAID,
                    player,
                    turnId,
                    Map.of("amount", "50")
            );

            return save(result(true, "PAY_BAIL", actionId,
                    "OK", "Đã nộp tiền bảo lãnh ra tù.",
                    playerId, turnId));
        }
    }

    // ================= USE ITEM =================

    public synchronized ActionResult useItem(
            String roomId,
            String playerId,
            int turnId,
            String itemId,
            ItemTargetContext context,
            String actionId) {

        synchronized (room) {
            ActionResult old = findDuplicate(
                    "USE_ITEM", roomId, playerId, turnId, actionId
            );
            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId, "USE_ITEM", actionId
            );
            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();
            if (player.isBot()) {
                return save(result(false, "USE_ITEM", actionId,
                        "BOT_CONTROLLED", "Lượt này do Bot điều khiển.",
                        playerId, turnId));
            }

            reclaimHumanControl(player, turnId);

            if (itemId == null || itemId.isBlank()
                    || player.getInventory() == null
                    || !player.getInventory().hasItem(itemId)) {
                return save(result(false, "USE_ITEM", actionId,
                        "ITEM_NOT_FOUND", "Không tìm thấy vật phẩm trong túi đồ.",
                        playerId, turnId));
            }

            Item item = player.getInventory().getItemById(itemId);
            TurnPhase phase = getActionPhase();
            boolean validPhase = (item.getTriggerPhase() == ItemTriggerPhase.PRE_ROLL
                    && phase == TurnPhase.AWAITING_ROLL)
                    || (item.getTriggerPhase() == ItemTriggerPhase.AWAITING_DECISION
                    && phase == TurnPhase.AWAITING_DECISION);
            if (!validPhase) {
                return save(result(false, "USE_ITEM", actionId,
                        "INVALID_PHASE", "Không thể dùng vật phẩm ở giai đoạn hiện tại.",
                        playerId, turnId));
            }

            if (player.getStatus() != PlayerStatus.ACTIVE) {
                return save(result(false, "USE_ITEM", actionId,
                        "PLAYER_NOT_ACTIVE", "Người chơi không ở trạng thái ACTIVE.",
                        playerId, turnId));
            }

            if (!item.canUse(player, room, context)) {
                return save(result(false, "USE_ITEM", actionId,
                        "CANNOT_USE_ITEM", "Không đủ điều kiện sử dụng vật phẩm.",
                        playerId, turnId));
            }

            if (!item.applyEffect(player, room, context)) {
                return save(result(false, "USE_ITEM", actionId,
                        "ITEM_EFFECT_FAILED", "Kích hoạt vật phẩm thất bại.",
                        playerId, turnId));
            }

            player.getInventory().removeItem(itemId);

            if ("FORTUNE_SWAP".equals(item.getId()) && phase == TurnPhase.AWAITING_DECISION) {
                swappedPositionThisTurn = true;
            }

            emit(
                    EngineEvent.Type.ITEM_USED,
                    player,
                    turnId,
                    Map.of(
                            "itemId", item.getId(),
                            "itemName", item.getName()
                    )
            );

            return save(result(true, "USE_ITEM", actionId,
                    "OK", "Đã sử dụng vật phẩm.",
                    playerId, turnId));
        }
    }

    // ================= UPGRADE PROPERTY =================

    public synchronized boolean upgradeProperty(
            String playerId, int turnId, int tileIndex) {
        return upgradePropertyInternal(
                room.getId(), playerId, turnId, tileIndex, newId(), false
        ).isSuccess();
    }

    public synchronized ActionResult upgradeProperty(
            String roomId, String playerId, int turnId, int tileIndex, String actionId) {
        return upgradePropertyInternal(
                roomId, playerId, turnId, tileIndex, actionId, true
        );
    }

    private ActionResult upgradePropertyInternal(
            String roomId,
            String playerId,
            int turnId,
            int tileIndex,
            String actionId,
            boolean humanAction) {

        synchronized (room) {
            ActionResult old = findDuplicate(
                    "UPGRADE_PROPERTY", roomId, playerId, turnId, actionId
            );
            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId, "UPGRADE_PROPERTY", actionId
            );
            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();
            if (humanAction && player.isBot()) {
                return save(result(false, "UPGRADE_PROPERTY", actionId,
                        "BOT_CONTROLLED", "Lượt này do Bot điều khiển.",
                        playerId, turnId));
            }

            if (getActionPhase() != TurnPhase.AWAITING_DECISION
                    || player.getStatus() != PlayerStatus.ACTIVE) {
                return save(result(false, "UPGRADE_PROPERTY", actionId,
                        "INVALID_PHASE", "Chỉ có thể nâng cấp công trình trong pha quyết định.",
                        playerId, turnId));
            }

            Tile tile = room.getBoard().getTile(tileIndex);
            if (!(tile instanceof StreetProperty)) {
                return save(result(false, "UPGRADE_PROPERTY", actionId,
                        "NOT_STREET_PROPERTY", "Ô chỉ định không phải đất ở.",
                        playerId, turnId));
            }

            StreetProperty street = (StreetProperty) tile;
            if (street.getOwner() != player) {
                return save(result(false, "UPGRADE_PROPERTY", actionId,
                        "NOT_OWNER", "Bạn không sở hữu ô đất này.",
                        playerId, turnId));
            }

            if (!street.canUpgrade()) {
                return save(result(false, "UPGRADE_PROPERTY", actionId,
                        "CANNOT_UPGRADE", "Ô đất không thể nâng cấp thêm.",
                        playerId, turnId));
            }

            int cost = street.getBuildingCost();
            if (!player.deductMoney(cost)) {
                return save(result(false, "UPGRADE_PROPERTY", actionId,
                        "NOT_ENOUGH_MONEY", "Không đủ tiền để nâng cấp công trình.",
                        playerId, turnId));
            }

            if (humanAction) {
                reclaimHumanControl(player, turnId);
            }

            street.upgradeBuilding();

            emit(
                    EngineEvent.Type.PROPERTY_UPGRADED,
                    player,
                    turnId,
                    Map.of(
                            "tileIndex", String.valueOf(street.getIndex()),
                            "tileName", street.getName(),
                            "cost", String.valueOf(cost),
                            "houseCount", String.valueOf(street.getHouseCount()),
                            "hasHotel", String.valueOf(street.isHasHotel())
                    )
            );

            return save(result(true, "UPGRADE_PROPERTY", actionId,
                    "OK", "Nâng cấp công trình thành công.",
                    playerId, turnId));
        }
    }

    // ================= SURRENDER =================

    public synchronized ActionResult surrenderPlayer(
            String roomId, String playerId, String actionId) {

        synchronized (room) {
            int turnId = room.getTurnId();

            ActionResult old = findDuplicate(
                    "SURRENDER", roomId, playerId, turnId, actionId
            );
            if (old != null) {
                return old;
            }

            if (!validActionId(actionId)) {
                return result(false, "SURRENDER", actionId,
                        "INVALID_ACTION_ID", "actionId không hợp lệ.",
                        playerId, turnId);
            }

            if (!validRoom(roomId)) {
                return save(result(false, "SURRENDER", actionId,
                        "INVALID_ROOM", "roomId không hợp lệ.",
                        playerId, turnId));
            }

            if (room.getStatus() != RoomStatus.PLAYING) {
                return save(result(false, "SURRENDER", actionId,
                        "ROOM_NOT_PLAYING", "Phòng không ở trạng thái PLAYING.",
                        playerId, turnId));
            }

            if (playerId == null || playerId.isBlank()) {
                return save(result(false, "SURRENDER", actionId,
                        "INVALID_PLAYER", "playerId không hợp lệ.",
                        null, turnId));
            }

            Player target = null;
            for (Player p : room.getPlayers()) {
                if (p.getId().equals(playerId)) {
                    target = p;
                    break;
                }
            }
            if (target == null) {
                return save(result(false, "SURRENDER", actionId,
                        "INVALID_PLAYER", "Không tìm thấy người chơi.",
                        playerId, turnId));
            }

            if (target.getStatus() == PlayerStatus.BANKRUPT) {
                return save(result(false, "SURRENDER", actionId,
                        "ALREADY_BANKRUPT", "Người chơi đã phá sản.",
                        playerId, turnId));
            }

            boolean isCurrent = room.getCurrentPlayer() == target;
            target.declareBankruptcy(null);

            emit(
                    EngineEvent.Type.PLAYER_SURRENDERED,
                    target,
                    turnId,
                    Collections.emptyMap()
            );

            if (!checkGameFinished() && isCurrent) {
                advanceTurn(target, turnId);
            }

            return save(result(true, "SURRENDER", actionId,
                    "OK", "Người chơi đã đầu hàng.",
                    playerId, turnId));
        }
    }

    // ================= END TURN =================

    /**
     * Kết thúc lượt.
     * Giữ nguyên phương thức ban đầu trong khung.
     */
    public synchronized boolean endTurn(String playerId, int turnId) {

        return endTurnInternal(
                room.getId(), playerId, turnId, newId(), false
        ).isSuccess();
    }

    public synchronized ActionResult endTurn(
            String roomId, String playerId,
            int turnId, String actionId) {

        return endTurnInternal(
                roomId, playerId, turnId, actionId, true
        );
    }

    private ActionResult endTurnInternal(
            String roomId,
            String playerId,
            int turnId,
            String actionId,
            boolean humanAction) {

        synchronized (room) {

            ActionResult old = findDuplicate(
                    "END_TURN", roomId,
                    playerId, turnId, actionId
            );

            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId,
                    "END_TURN", actionId
            );

            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();

            if (humanAction && player.isBot()) {
                return save(result(false, "END_TURN",
                        actionId, "BOT_CONTROLLED",
                        "Lượt này do Bot điều khiển.",
                        playerId, turnId));
            }

            boolean skipTurnInJail =
                    getActionPhase() == TurnPhase.AWAITING_ROLL
                            && player.getStatus() == PlayerStatus.IN_JAIL;

            if (getActionPhase() != TurnPhase.AWAITING_DECISION
                    && !skipTurnInJail) {

                return save(result(false, "END_TURN",
                        actionId, "INVALID_PHASE",
                        "Chưa thể kết thúc lượt.",
                        playerId, turnId));
            }

            if (humanAction) {
                reclaimHumanControl(player, turnId);
            }

            return save(finishTurn(
                    player, playerId, turnId,
                    actionId, skipTurnInJail
            ));
        }
    }

    private ActionResult finishTurn(
            Player player,
            String playerId,
            int turnId,
            String actionId,
            boolean skipTurnInJail) {

        // Người chơi đang trong tù thì cập nhật lượt tù trước khi bỏ lượt.
        if (skipTurnInJail) {
            Tile jailTile = room.getBoard().getTile(10);
            if (jailTile != null) {
                jailTile.onLand(player, room);
            }
            if (checkGameFinished()) {
                return result(true, "END_TURN", actionId,
                        "GAME_FINISHED", "Ván chơi đã kết thúc.",
                        playerId, turnId);
            }
            advanceTurn(player, turnId);

            return result(true, "END_TURN", actionId,
                    "OK", "Đã bỏ lượt.",
                    playerId, turnId);
        }

        if (checkGameFinished()) {
            return result(true, "END_TURN", actionId,
                    "GAME_FINISHED", "Ván chơi đã kết thúc.",
                    playerId, turnId);
        }

        // Gieo đôi thì được tiếp tục gieo.
        if (player.getStatus() == PlayerStatus.ACTIVE
                && room.getLastDice() != null
                && room.getLastDice().isDouble()
                && consecutiveDoubles > 0) {

            setActionPhase(TurnPhase.AWAITING_ROLL);

            emit(
                    EngineEvent.Type.EXTRA_ROLL_GRANTED,
                    player,
                    turnId,
                    Collections.emptyMap()
            );

            return result(true, "END_TURN", actionId,
                    "EXTRA_ROLL",
                    "Gieo đôi, được tiếp tục lượt.",
                    playerId, turnId);
        }

        advanceTurn(player, turnId);

        return result(true, "END_TURN", actionId,
                "OK", "Đã kết thúc lượt.",
                playerId, turnId);
    }

    private void advanceTurn(Player previousPlayer, int previousTurnId) {

        if (checkGameFinished()) {
            return;
        }

        clearBotControl();

        room.nextTurn();

        consecutiveDoubles = 0;
        swappedPositionThisTurn = false;
        room.setLastDice(null);
        currentPhase = TurnPhase.AWAITING_ROLL;

        Player nextPlayer = room.getCurrentPlayer();
        room.getEventManager().onTurnStart(room, nextPlayer);

        emit(
                EngineEvent.Type.TURN_ENDED,
                previousPlayer,
                previousTurnId,
                Map.of(
                        "nextPlayerId",
                        nextPlayer == null ? "" : nextPlayer.getId(),
                        "nextTurnId",
                        String.valueOf(room.getTurnId())
                )
        );

        if (room.getStatus() == RoomStatus.PLAYING
                && room.getTurnId() >= 10
                && room.getTurnId() % 8 == 0) {
            GameEvent randomEvent = GameEventManager.createRandomEvent();
            if (randomEvent != null) {
                room.getEventManager().triggerEvent(randomEvent, room);
                emit(
                        EngineEvent.Type.SPECIAL_EVENT_TRIGGERED,
                        nextPlayer,
                        room.getTurnId(),
                        Map.of(
                                "eventId", randomEvent.getId(),
                                "eventName", randomEvent.getName(),
                                "description", randomEvent.getDescription(),
                                "durationTurns", String.valueOf(randomEvent.getDurationTurns())
                        )
                );
            }
        }

        checkGameFinished();
    }

    // ================= TIMEOUT =================

    /**
     * Được TurnTimerService gọi khi hết giờ.
     */
    public synchronized ActionResult handleTimeout(
            String roomId, String playerId, int turnId,
            String actionId) {

        synchronized (room) {

            ActionResult old = findDuplicate(
                    "TIMEOUT", roomId,
                    playerId, turnId, actionId
            );

            if (old != null) {
                return old;
            }

            ActionResult invalid = validateBase(
                    roomId, playerId, turnId,
                    "TIMEOUT", actionId
            );

            if (invalid != null) {
                return save(invalid);
            }

            Player player = room.getCurrentPlayer();

            if (player.isBot() || botControlActive) {
                return save(result(false, "TIMEOUT",
                        actionId, "BOT_ALREADY_ACTIVE",
                        "Bot đã tiếp quản lượt.",
                        playerId, turnId));
            }

            if (currentPhase != TurnPhase.AWAITING_ROLL
                    && currentPhase != TurnPhase.AWAITING_DECISION) {

                return save(result(false, "TIMEOUT",
                        actionId, "INVALID_PHASE",
                        "Không thể timeout ở phase này.",
                        playerId, turnId));
            }

            phaseBeforeTimeout = currentPhase;
            timeoutPlayerId = playerId;
            timeoutTurnId = turnId;
            botControlActive = true;

            currentPhase = TurnPhase.TIMEOUT;

            emit(
                    EngineEvent.Type.TURN_TIMED_OUT,
                    player,
                    turnId,
                    Collections.emptyMap()
            );

            emit(
                    EngineEvent.Type.BOT_TAKEOVER,
                    player,
                    turnId,
                    Collections.emptyMap()
            );

            return save(result(true, "TIMEOUT", actionId,
                    "OK", "Bot đã tiếp quản lượt.",
                    playerId, turnId));
        }
    }

    /**
     * Kiểm tra Bot có còn quyền xử lý lượt hiện tại không.
     */
    public synchronized boolean isBotTurnActive(
            String roomId, String playerId, int turnId) {

        synchronized (room) {

            if (!validRoom(roomId)
                    || room.getStatus() != RoomStatus.PLAYING
                    || room.getTurnId() != turnId) {
                return false;
            }

            Player player = room.getCurrentPlayer();

            return player != null
                    && player.getId().equals(playerId)
                    && (player.isBot()
                    || isTimeoutControl(playerId, turnId));
        }
    }

    /**
     * Lấy phase mà Bot được phép xử lý.
     */
    public synchronized TurnPhase getBotActionPhase(
            String roomId, String playerId, int turnId) {

        synchronized (room) {

            if (!isBotTurnActive(roomId, playerId, turnId)) {
                return null;
            }

            return getActionPhase();
        }
    }

    // ================= HUMAN RECLAIM =================

    private void reclaimHumanControl(Player player, int turnId) {

        if (isTimeoutControl(player.getId(), turnId)) {

            currentPhase = phaseBeforeTimeout;

            clearBotControl();

            emit(
                    EngineEvent.Type.HUMAN_CONTROL_RECLAIMED,
                    player,
                    turnId,
                    Collections.emptyMap()
            );
        }
    }

    private boolean isTimeoutControl(String playerId, int turnId) {

        return botControlActive
                && timeoutTurnId == turnId
                && playerId != null
                && playerId.equals(timeoutPlayerId);
    }

    private TurnPhase getActionPhase() {

        if (currentPhase == TurnPhase.TIMEOUT
                && phaseBeforeTimeout != null) {
            return phaseBeforeTimeout;
        }

        return currentPhase;
    }

    private void setActionPhase(TurnPhase nextPhase) {

        if (currentPhase == TurnPhase.TIMEOUT && botControlActive) {
            phaseBeforeTimeout = nextPhase;
        } else {
            currentPhase = nextPhase;
        }
    }

    private void clearBotControl() {
        botControlActive = false;
        timeoutPlayerId = null;
        timeoutTurnId = -1;
        phaseBeforeTimeout = null;
    }

    // ================= VALIDATE =================

    private ActionResult validateBase(
            String roomId, String playerId,
            int turnId, String type, String actionId) {

        if (!validActionId(actionId)) {
            return result(false, type, actionId,
                    "INVALID_ACTION_ID", "actionId không hợp lệ.",
                    playerId, turnId);
        }

        if (!validRoom(roomId)) {
            return result(false, type, actionId,
                    "INVALID_ROOM", "roomId không hợp lệ.",
                    playerId, turnId);
        }

        if (room.getStatus() != RoomStatus.PLAYING) {
            return result(false, type, actionId,
                    "ROOM_NOT_PLAYING",
                    "Phòng chưa bắt đầu hoặc đã kết thúc.",
                    playerId, turnId);
        }

        if (playerId == null || playerId.isBlank()) {
            return result(false, type, actionId,
                    "INVALID_PLAYER", "playerId không hợp lệ.",
                    null, turnId);
        }

        if (room.getTurnId() != turnId) {
            return result(false, type, actionId,
                    "STALE_TURN", "turnId đã cũ.",
                    playerId, turnId);
        }

        Player currentPlayer = room.getCurrentPlayer();

        if (currentPlayer == null
                || !currentPlayer.getId().equals(playerId)) {

            return result(false, type, actionId,
                    "NOT_YOUR_TURN", "Không phải lượt của người chơi này.",
                    playerId, turnId);
        }

        return null;
    }

    // ================= DUPLICATE ACTION =================

    private ActionResult findDuplicate(
            String type, String roomId,
            String playerId, int turnId, String actionId) {

        if (!validActionId(actionId)) {
            return result(false, type, actionId,
                    "INVALID_ACTION_ID", "actionId không hợp lệ.",
                    playerId, turnId);
        }

        ActionResult old = actionResults.get(actionId);

        if (old == null) {
            return null;
        }

        boolean sameAction =
                type.equals(old.getActionType())
                        && same(roomId, old.getRoomId())
                        && same(playerId, old.getPlayerId())
                        && turnId == old.getTurnId();

        if (sameAction) {
            return old;
        }

        return result(false, type, actionId,
                "ACTION_ID_REUSED",
                "actionId đã được dùng cho command khác.",
                playerId, turnId);
    }

    private ActionResult save(ActionResult actionResult) {

        String actionId = actionResult.getActionId();

        if (validActionId(actionId)) {

            actionResults.put(actionId, actionResult);

            while (actionResults.size() > MAX_ACTIONS) {
                String firstKey = actionResults.keySet()
                        .iterator().next();

                actionResults.remove(firstKey);
            }
        }

        return actionResult;
    }

    // ================= CHECK GAME FINISHED =================

    private boolean checkGameFinished() {

        if (room.getStatus() != RoomStatus.PLAYING) {
            return room.getStatus() == RoomStatus.FINISHED;
        }

        int activePlayers = 0;

        for (Player player : room.getPlayers()) {
            if (player.getStatus() != PlayerStatus.BANKRUPT) {
                activePlayers++;
            }
        }

        if (activePlayers <= 1) {

            room.setStatus(RoomStatus.FINISHED);
            currentPhase = TurnPhase.FINISHED;

            clearBotControl();

            emit(
                    EngineEvent.Type.GAME_FINISHED,
                    room.getCurrentPlayer(),
                    room.getTurnId(),
                    Map.of("remainingPlayers",
                            String.valueOf(activePlayers))
            );

            return true;
        }

        return false;
    }

    // ================= HELPER =================

    private boolean validRoom(String roomId) {
        return roomId != null && roomId.equals(room.getId());
    }

    private boolean validActionId(String actionId) {
        return actionId != null && !actionId.isBlank();
    }

    private boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private ActionResult result(
            boolean success,
            String type,
            String actionId,
            String code,
            String message,
            String playerId,
            int turnId) {

        return new ActionResult(
                success,
                type,
                actionId,
                code,
                message,
                room.getId(),
                playerId,
                turnId,
                currentPhase
        );
    }

    // =================================================
    // IMMUTABLE ACTION RESULT
    // =================================================

    public static final class ActionResult {

        private final boolean success;
        private final String actionType;
        private final String actionId;
        private final String code;
        private final String message;
        private final String roomId;
        private final String playerId;
        private final int turnId;
        private final TurnPhase phase;

        public ActionResult(
                boolean success, String actionType,
                String actionId, String code,
                String message, String roomId,
                String playerId, int turnId, TurnPhase phase) {

            this.success = success;
            this.actionType = actionType;
            this.actionId = actionId;
            this.code = code;
            this.message = message;
            this.roomId = roomId;
            this.playerId = playerId;
            this.turnId = turnId;
            this.phase = phase;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getActionType() {
            return actionType;
        }

        public String getActionId() {
            return actionId;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getRoomId() {
            return roomId;
        }

        public String getPlayerId() {
            return playerId;
        }

        public int getTurnId() {
            return turnId;
        }

        public TurnPhase getPhase() {
            return phase;
        }
    }

    // =================================================
    // IMMUTABLE ENGINE EVENT
    // =================================================

    public static final class EngineEvent {

        public enum Type {
            GAME_STARTED,
            DICE_ROLLED,
            TILE_RESOLVED,
            PROPERTY_BOUGHT,
            PLAYER_SENT_TO_JAIL,
            BAIL_PAID,
            ITEM_USED,
            PROPERTY_UPGRADED,
            SPECIAL_EVENT_TRIGGERED,
            PLAYER_SURRENDERED,
            TURN_TIMED_OUT,
            BOT_TAKEOVER,
            HUMAN_CONTROL_RECLAIMED,
            EXTRA_ROLL_GRANTED,
            TURN_ENDED,
            GAME_FINISHED
        }

        private final String eventId;
        private final Type type;
        private final String roomId;
        private final String playerId;
        private final int turnId;
        private final TurnPhase phase;
        private final Instant createdAt;
        private final Map<String, String> data;

        public EngineEvent(
                Type type, String roomId,
                String playerId, int turnId,
                TurnPhase phase, Map<String, String> data) {

            this.eventId = UUID.randomUUID().toString();
            this.type = type;
            this.roomId = roomId;
            this.playerId = playerId;
            this.turnId = turnId;
            this.phase = phase;
            this.createdAt = Instant.now();

            this.data = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            data == null
                                    ? Collections.emptyMap()
                                    : data
                    )
            );
        }

        public String getEventId() {
            return eventId;
        }

        public Type getType() {
            return type;
        }

        public String getRoomId() {
            return roomId;
        }

        public String getPlayerId() {
            return playerId;
        }

        public int getTurnId() {
            return turnId;
        }

        public TurnPhase getPhase() {
            return phase;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Map<String, String> getData() {
            return data;
        }
    }
}
