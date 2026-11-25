package model.event;

import model.GameRoom;
import model.Player;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
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
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class ActiveEventEntry {
        final GameEvent event;
        int remainingTurns;

        ActiveEventEntry(GameEvent event, int remainingTurns) {
            this.event = event;
            this.remainingTurns = remainingTurns;
        }
    }

    private final List<ActiveEventEntry> activeEntries;

    public GameEventManager() {
        this.activeEntries = new ArrayList<>();
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
            activeEntries.add(new ActiveEventEntry(event, event.getDurationTurns()));
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
        List<GameEvent> expiredEvents = new ArrayList<>();
        Iterator<ActiveEventEntry> iterator = activeEntries.iterator();
        while (iterator.hasNext()) {
            ActiveEventEntry entry = iterator.next();
            entry.remainingTurns -= 1;
            if (entry.remainingTurns <= 0) {
                expiredEvents.add(entry.event);
                iterator.remove();
            }
        }
        for (GameEvent expired : expiredEvents) {
            expired.onExpire(room);
        }
    }

    public synchronized boolean hasActiveEventId(String id) {
        if (id == null) {
            return false;
        }
        for (ActiveEventEntry entry : activeEntries) {
            if (id.equals(entry.event.getId())) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isTileFrozenByOtherEvent(FrozenTileEvent expiring, int idx) {
        for (ActiveEventEntry entry : activeEntries) {
            if (entry.event instanceof FrozenTileEvent && entry.event != expiring) {
                FrozenTileEvent other = (FrozenTileEvent) entry.event;
                if (other.getFrozenTileIndices().contains(idx)) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized int getRemainingTurns(GameEvent event) {
        if (event == null) {
            return 0;
        }
        for (ActiveEventEntry entry : activeEntries) {
            if (entry.event == event) {
                return entry.remainingTurns;
            }
        }
        return 0;
    }

    public static GameEvent createRandomEvent() {
        int pick = RANDOM.nextInt(6);
        switch (pick) {
            case 0:
                return new RichTaxEvent();
            case 1:
                return new PropertyBoomEvent();
            case 2:
                return new ContinentalDriftEvent();
            case 3:
                return new StimulusDayEvent();
            case 4:
                return new EarthquakeEvent();
            default:
                return new FrozenTileEvent();
        }
    }

    /**
     * Lấy danh sách các sự kiện đang có hiệu lực.
     */
    public synchronized List<GameEvent> getActiveEvents() {
        List<GameEvent> result = new ArrayList<>(activeEntries.size());
        for (ActiveEventEntry entry : activeEntries) {
            result.add(entry.event);
        }
        return result;
    }
}

