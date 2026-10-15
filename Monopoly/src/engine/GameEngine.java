package engine;

import model.GameRoom;

/**
 * Game Engine Authoritative chịu trách nhiệm điều phối luật chơi,
 * validate lượt đi và thực hiện hành động cờ tỷ phú.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI (SERVER AUTHORITATIVE):
 * - Server là nguồn sự thật duy nhất (Single Source of Truth).
 * - Không tin tưởng dữ liệu vị trí/tiền/xúc xắc từ client gửi lên.
 * - Mọi action phải kiểm tra: đúng người chơi, đúng phase và turnId khớp với phòng.
 * - Gọi trực tiếp tile.onLand(player, room) để vận dụng tính đa hình (không dùng if-else).
 */
public class GameEngine {
    private final GameRoom room;
    private TurnPhase currentPhase;

    public GameEngine(GameRoom room) {
        this.room = room;
        this.currentPhase = TurnPhase.AWAITING_ROLL;
    }

    public synchronized TurnPhase getCurrentPhase() {
        // TODO: Trả về giai đoạn hiện tại của lượt chơi
        return null;
    }

    public synchronized GameRoom getRoom() {
        // TODO: Trả về phòng cờ do engine điều phối
        return null;
    }

    /**
     * Bắt đầu ván chơi.
     */
    public synchronized boolean startGame() {
        // TODO:
        // 1. Kiểm tra số lượng người chơi (tối thiểu 2 người)
        // 2. Chuyển trạng thái phòng sang PLAYING
        // 3. Đặt phase về AWAITING_ROLL
        return false;
    }

    /**
     * Xử lý hành động gieo xúc xắc từ người chơi.
     * Kiểm tra lượt, phase và turnId hợp lệ.
     */
    public synchronized boolean rollDice(String playerId, int turnId) {
        // TODO:
        // 1. Validate: phòng đang PLAYING, phase là AWAITING_ROLL, đúng lượt người chơi và đúng turnId
        // 2. Tự sinh xúc xắc ngẫu nhiên qua Dice
        // 3. Cập nhật vị trí mới trên bàn cờ, cộng thưởng nếu đi qua ô GO
        // 4. Gọi hành vi đa hình: room.getBoard().getTile(newPos).onLand(player, room)
        // 5. Chuyển phase sang AWAITING_DECISION
        return false;
    }

    /**
     * Kết thúc lượt hiện tại và chuyển quyền cho người chơi kế tiếp.
     */
    public synchronized boolean endTurn(String playerId, int turnId) {
        // TODO:
        // 1. Validate: phase là AWAITING_DECISION, đúng lượt người chơi và đúng turnId
        // 2. Gọi room.nextTurn() để chuyển lượt
        // 3. Đặt phase về AWAITING_ROLL cho người chơi tiếp theo
        return false;
    }
}
