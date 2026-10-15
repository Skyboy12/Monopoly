package server.service;

import engine.GameEngine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Service quản lý Turn Timer và cơ chế Timeout / AFK cho các phòng chơi.
 * Tham khảo docs/member-3-game-engine.md & workflow.md:
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI (TURN TIMER & BOT TAKEOVER):
 * ════════════════════════════════════════════════════════════════════════════════
 * - Sử dụng ScheduledExecutorService để đếm ngược thời gian cho mỗi lượt (mặc
 * định 30 giây).
 * - Khi lượt mới bắt đầu (turnId mới):
 * 1. Hủy bỏ (cancel) timer cũ của phòng nếu đang chạy dở.
 * 2. Khởi tạo một timer mới cho lượt hiện tại.
 * - Khi hết thời gian (Timeout):
 * 1. Chuyển quyền xử lý cho BotController.processBotTurn(engine).
 * 2. Đánh dấu người chơi nếu AFK liên tiếp để tự động chạy bot ở các lượt sau.
 * - Khi người chơi tự thực hiện action hợp lệ trước khi hết giờ -> hủy bỏ timer
 * này.
 */
public class TurnTimerService {
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> roomTimers;
    private final int turnTimeoutSeconds;

    public TurnTimerService(ScheduledExecutorService scheduler, int turnTimeoutSeconds) {
        this.scheduler = scheduler;
        this.roomTimers = new ConcurrentHashMap<>();
        this.turnTimeoutSeconds = turnTimeoutSeconds;
    }

    /**
     * Bắt đầu đếm ngược thời gian lượt chơi cho một phòng cờ.
     *
     * @param roomId Mã phòng chơi
     * @param turnId Số hiệu lượt đi hiện tại
     * @param engine GameEngine của phòng cờ
     */
    public synchronized void startTurnTimer(String roomId, int turnId, GameEngine engine) {
        // TODO:
        // 1. Hủy timer cũ của phòng (cancelTurnTimer) nếu đang chạy
        // 2. Lên lịch chạy task sau turnTimeoutSeconds:
        // - Kiểm tra nếu phòng vẫn đang ở turnId này thì kích hoạt
        // BotController.processBotTurn(engine)
        // 3. Lưu ScheduledFuture vào roomTimers
    }

    /**
     * Hủy bỏ bộ đếm thời gian khi người chơi hoàn thành lượt đúng hạn.
     *
     * @param roomId Mã phòng chơi
     */
    public synchronized void cancelTurnTimer(String roomId) {
        // TODO: Lấy timer từ roomTimers, gọi cancel(true) và loại bỏ khỏi map
    }
}
