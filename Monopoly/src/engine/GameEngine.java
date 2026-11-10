package engine;

import model.Dice;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.tile.PropertyTile;
import model.tile.Tile;

/**
 * Game Engine Authoritative chịu trách nhiệm điều phối luật chơi.
 * Server tự kiểm tra lượt, tự gieo xúc xắc và tự cập nhật trạng thái.
 */
public class GameEngine {
    private final GameRoom room;
    private TurnPhase currentPhase;

    // Đếm số lần gieo được đôi liên tiếp trong cùng một lượt.
    private int consecutiveDoubles;

    public GameEngine(GameRoom room) {
        if (room == null) {
            throw new IllegalArgumentException("GameRoom không được null");
        }
        this.room = room;
        this.currentPhase = TurnPhase.AWAITING_ROLL;
        this.consecutiveDoubles = 0;
    }

    public synchronized TurnPhase getCurrentPhase() {
        return currentPhase;
    }

    public synchronized GameRoom getRoom() {
        return room;
    }

    /** Bắt đầu ván chơi khi phòng có ít nhất 2 người. */
    public synchronized boolean startGame() {
        if (room.getStatus() != RoomStatus.WAITING || room.getPlayers().size() < 2) {
            return false;
        }

        room.setStatus(RoomStatus.PLAYING);
        room.setLastDice(null);
        consecutiveDoubles = 0;
        currentPhase = TurnPhase.AWAITING_ROLL;
        return true;
    }

    /**
     * Gieo xúc xắc và xử lý ô mà người chơi đáp xuống.
     * turnId phải khớp với turnId hiện tại của GameRoom.
     */
    public synchronized boolean rollDice(String playerId, int turnId) {
        if (!isValidAction(playerId, turnId, TurnPhase.AWAITING_ROLL)) {
            return false;
        }

        Player player = room.getCurrentPlayer();
        if (player.getStatus() != PlayerStatus.ACTIVE) {
            return false;
        }

        // Xúc xắc được sinh ở server, không nhận kết quả từ client.
        Dice dice = new Dice();
        dice.roll();
        room.setLastDice(dice);

        if (dice.isDouble()) {
            consecutiveDoubles++;
        } else {
            consecutiveDoubles = 0;
        }

        // Theo luật dự án: gieo đôi 3 lần liên tiếp thì vào tù, không di chuyển.
        if (consecutiveDoubles == 3) {
            player.sendToJail(10);
            consecutiveDoubles = 0;
            currentPhase = TurnPhase.AWAITING_DECISION;
            return true;
        }

        // Player.move() tự xử lý thưởng $200 khi đi qua ô GO.
        player.move(dice.getTotal());

        Tile tile = room.getBoard().getTile(player.getPosition());
        currentPhase = TurnPhase.RESOLVING_ACTION;
        try {
            // Dùng đa hình: mỗi loại Tile tự xử lý hành vi của nó.
            if (tile != null) {
                tile.onLand(player, room);
            }
        } finally {
            currentPhase = TurnPhase.AWAITING_DECISION;
        }

        return true;
    }

    /**
     * Mua ô tài sản hiện tại nếu ô chưa có chủ và người chơi đủ tiền.
     * Đây là phương thức dùng chung cho người chơi thật và BotController.
     */
    public synchronized boolean buyProperty(String playerId, int turnId) {
        if (!isValidAction(playerId, turnId, TurnPhase.AWAITING_DECISION)) {
            return false;
        }

        Player player = room.getCurrentPlayer();
        if (player.getStatus() != PlayerStatus.ACTIVE) {
            return false;
        }

        Tile tile = room.getBoard().getTile(player.getPosition());
        if (!(tile instanceof PropertyTile)) {
            return false;
        }

        PropertyTile property = (PropertyTile) tile;
        if (property.hasOwner() || player.getMoney() < property.getPrice()) {
            return false;
        }

        player.buyProperty(property);
        return property.getOwner() == player;
    }

    /**
     * Trả tiền chuộc để ra tù. Sau khi trả thành công, người chơi có thể gieo xúc xắc.
     * Mức tiền $50 được lấy từ Player.payBail() trong model hiện tại.
     */
    public synchronized boolean payBail(String playerId, int turnId) {
        if (!isValidAction(playerId, turnId, TurnPhase.AWAITING_ROLL)) {
            return false;
        }

        Player player = room.getCurrentPlayer();
        return player.getStatus() == PlayerStatus.IN_JAIL && player.payBail();
    }

    /**
     * Kết thúc lượt. Nếu vừa gieo đôi thì người chơi được gieo tiếp,
     * không tăng turnId và không chuyển sang người tiếp theo.
     * Người đang ở tù có thể bỏ lượt nếu chưa trả được tiền chuộc.
     */
    public synchronized boolean endTurn(String playerId, int turnId) {
        if (room.getStatus() != RoomStatus.PLAYING
                || playerId == null
                || room.getTurnId() != turnId) {
            return false;
        }

        Player player = room.getCurrentPlayer();
        if (player == null || !player.getId().equals(playerId)) {
            return false;
        }

        boolean normalDecision = currentPhase == TurnPhase.AWAITING_DECISION;
        boolean skipJailTurn = currentPhase == TurnPhase.AWAITING_ROLL
                && player.getStatus() == PlayerStatus.IN_JAIL;

        if (!normalDecision && !skipJailTurn) {
            return false;
        }

        if (skipJailTurn) {
            player.setTurnsInJail(player.getTurnsInJail() + 1);
        }

        // Nếu chỉ còn tối đa một người chưa phá sản thì kết thúc ván.
        if (countNonBankruptPlayers() <= 1) {
            room.setStatus(RoomStatus.FINISHED);
            currentPhase = TurnPhase.FINISHED;
            return true;
        }

        // Hai lần gieo đôi đầu tiên cho phép cùng người chơi tiếp tục lượt.
        if (normalDecision
                && player.getStatus() == PlayerStatus.ACTIVE
                && room.getLastDice() != null
                && room.getLastDice().isDouble()
                && consecutiveDoubles > 0) {
            currentPhase = TurnPhase.AWAITING_ROLL;
            return true;
        }

        room.nextTurn();
        consecutiveDoubles = 0;
        currentPhase = TurnPhase.AWAITING_ROLL;
        return true;
    }

    /** Kiểm tra chung trạng thái phòng, phase, turnId và người chơi hiện tại. */
    private boolean isValidAction(String playerId, int turnId, TurnPhase requiredPhase) {
        if (room.getStatus() != RoomStatus.PLAYING
                || currentPhase != requiredPhase
                || playerId == null
                || room.getTurnId() != turnId) {
            return false;
        }

        Player currentPlayer = room.getCurrentPlayer();
        return currentPlayer != null && currentPlayer.getId().equals(playerId);
    }

    private int countNonBankruptPlayers() {
        int count = 0;
        for (Player player : room.getPlayers()) {
            if (player.getStatus() != PlayerStatus.BANKRUPT) {
                count++;
            }
        }
        return count;
    }
}
