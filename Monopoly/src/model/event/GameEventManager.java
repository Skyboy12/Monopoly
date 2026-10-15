package model.event;

import model.GameRoom;
import model.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý vòng đời và thời hạn hiệu lực của các sự kiện đang diễn ra trong phòng cờ.
 * Tham khảo từ docs/event-and-item.md Mục 3.2.
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI:
 * ════════════════════════════════════════════════════════════════════════════════
 * - triggerEvent(event, room): Đưa sự kiện vào danh sách activeEvents và kích hoạt event.onTrigger(room).
 * - onTurnStart(room, currentPlayer): Được GameEngine gọi ở đầu mỗi lượt chơi:
 *   1. Giảm thời gian hiệu lực (durationTurns) của các sự kiện đang chạy.
 *   2. Khi sự kiện hết hạn, gọi event.onExpire(room) và loại bỏ khỏi danh sách.
 * - Periodic Event Trigger: Sau mỗi 4 - 5 vòng chơi (lap), tự động kích hoạt 1 sự kiện ngẫu nhiên.
 */
public class GameEventManager {
    private final List<GameEvent> activeEvents;

    public GameEventManager() {
        this.activeEvents = new ArrayList<>();
    }

    /**
     * Kích hoạt một sự kiện mới trong phòng cờ.
     */
    public synchronized void triggerEvent(GameEvent event, GameRoom room) {
        // TODO:
        // 1. Thêm event vào danh sách activeEvents
        // 2. Gọi event.onTrigger(room)
        // 3. Thông báo sự kiện bắt đầu cho các client trong phòng
    }

    /**
     * Xử lý thời hạn của các sự kiện khi bắt đầu một lượt mới.
     */
    public synchronized void onTurnStart(GameRoom room, Player currentPlayer) {
        // TODO:
        // 1. Duyệt qua danh sách activeEvents
        // 2. Giảm số lượt còn lại của từng sự kiện
        // 3. Nếu sự kiện hết hạn -> gọi event.onExpire(room) và xóa khỏi activeEvents
    }

    /**
     * Lấy danh sách các sự kiện đang có hiệu lực.
     */
    public synchronized List<GameEvent> getActiveEvents() {
        // TODO: Trả về danh sách các sự kiện đang diễn ra
        return null;
    }
}

