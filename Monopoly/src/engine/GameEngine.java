package engine;

import model.Dice;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.tile.PropertyTile;
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

            if (player.getStatus() != PlayerStatus.ACTIVE) {
                return save(result(false, "ROLL_DICE", actionId,
                        "PLAYER_NOT_ACTIVE",
                        "Người chơi không ở trạng thái ACTIVE.",
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

        Dice dice = new Dice();
        dice.roll();

        room.setLastDice(dice);

        if (dice.isDouble()) {
            consecutiveDoubles++;
        } else {
            consecutiveDoubles = 0;
        }

        emit(
                EngineEvent.Type.DICE_ROLLED,
                player,
                turnId,
                Map.of(
                        "dice1", String.valueOf(dice.getDice1()),
                        "dice2", String.valueOf(dice.getDice2()),
                        "total", String.valueOf(dice.getTotal())
                )
        );

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

        currentPhase = TurnPhase.RESOLVING_ACTION;

        // Player.move() tự cộng $200 khi đi qua GO.
        player.move(dice.getTotal());

        // Gọi trực tiếp phương thức đa hình.
        Tile tile = room.getBoard().getTile(player.getPosition());

        if (tile != null) {
            try {
                tile.onLand(player, room);

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
                currentPhase = TurnPhase.AWAITING_DECISION;

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

        currentPhase = TurnPhase.AWAITING_DECISION;

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

        // Người chơi đang trong tù thì bỏ lượt.
        if (skipTurnInJail) {
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
        room.setLastDice(null);
        currentPhase = TurnPhase.AWAITING_ROLL;

        Player nextPlayer = room.getCurrentPlayer();

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
