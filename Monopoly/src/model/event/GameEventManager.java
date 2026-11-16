package model.event;

import model.GameRoom;
import model.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    private final Map<GameEvent, Integer> remainingTurns;

    public GameEventManager() {
        this.activeEvents = new ArrayList<>();
        this.remainingTurns = new HashMap<>();
    }

    /**
     * Kích hoạt một sự kiện mới trong phòng cờ.
     */
    public synchronized void triggerEvent(GameEvent event, GameRoom room) {
        if (event == null || room == null) {
            return;
        }
        event.onTrigger(room);
        if (event.getDurationTurns() > 0) {
            activeEvents.add(event);
            remainingTurns.put(event, event.getDurationTurns());
        } else {
            event.onExpire(room);
        }
    }

    /**
     * Xử lý thời hạn của các sự kiện khi bắt đầu một lượt mới.
     */
    public synchronized void onTurnStart(GameRoom room, Player currentPlayer) {
        if (room == null) {
            return;
        }
        Iterator<GameEvent> iterator = activeEvents.iterator();
        while (iterator.hasNext()) {
            GameEvent event = iterator.next();
            int turnsLeft = remainingTurns.getOrDefault(event, 0) - 1;
            if (turnsLeft <= 0) {
                event.onExpire(room);
                remainingTurns.remove(event);
                iterator.remove();
            } else {
                remainingTurns.put(event, turnsLeft);
            }
        }
    }

    /**
     * Lấy danh sách các sự kiện đang có hiệu lực.
     */
    public synchronized List<GameEvent> getActiveEvents() {
        return new ArrayList<>(activeEvents);
    }
}

