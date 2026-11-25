package server.service;

import engine.GameEngine;
import engine.GameEngine.ActionResult;
import engine.bot.BotController;

import model.GameRoom;
import model.Player;
import model.RoomStatus;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service quản lý Turn Timer và cơ chế Timeout / AFK
 * cho các phòng chơi.
 */
public class TurnTimerService {

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> roomTimers;
    private final ConcurrentHashMap<String, Long> roomDeadlines;
    private final int turnTimeoutSeconds;

    public TurnTimerService(ScheduledExecutorService scheduler,
                            int turnTimeoutSeconds) {

        if (scheduler == null || turnTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Tham số không hợp lệ.");
        }

        this.scheduler = scheduler;
        this.roomTimers = new ConcurrentHashMap<>();
        this.roomDeadlines = new ConcurrentHashMap<>();
        this.turnTimeoutSeconds = turnTimeoutSeconds;
    }

    /**
     * Bắt đầu đếm ngược thời gian lượt chơi cho một phòng cờ.
     *
     * @param roomId Mã phòng chơi
     * @param turnId Số hiệu lượt đi hiện tại
     * @param engine GameEngine của phòng cờ
     */
    public void startTurnTimer(
            String roomId, int turnId, GameEngine engine) {

        if (roomId == null || engine == null
                || !roomId.equals(engine.getRoom().getId())) {
            return;
        }

        GameRoom room = engine.getRoom();

        synchronized (room) {

            // Kiểm tra phòng và turnId.
            if (room.getStatus() != RoomStatus.PLAYING
                    || room.getTurnId() != turnId) {
                return;
            }

            Player player = room.getCurrentPlayer();

            if (player == null) {
                return;
            }

            String playerId = player.getId();

            if (scheduler.isShutdown()) {
                return;
            }

            // 1. Hủy timer cũ của phòng.
            cancelTurnTimer(roomId);

            if (player.isBot()) {
                AtomicReference<ScheduledFuture<?>> reference = new AtomicReference<>();
                ScheduledFuture<?> future = scheduler.schedule(
                        () -> handleDedicatedBotTurn(roomId, playerId, turnId, engine, reference),
                        1,
                        TimeUnit.SECONDS
                );
                reference.set(future);
                roomTimers.put(roomId, future);
                roomDeadlines.put(roomId, System.currentTimeMillis() + 1000L);
                return;
            }

            /*
             * 2. Lên lịch timeout.
             * Dùng AtomicReference để kiểm tra timer này
             * có còn là timer hiện tại của phòng không.
             */
            AtomicReference<ScheduledFuture<?>> reference =
                    new AtomicReference<>();

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> handleTimeout(
                            roomId, playerId, turnId, engine, reference
                    ),
                    turnTimeoutSeconds,
                    TimeUnit.SECONDS
            );

            reference.set(future);

            // 3. Lưu timer và deadline vào map.
            roomTimers.put(roomId, future);
            roomDeadlines.put(roomId, System.currentTimeMillis() + turnTimeoutSeconds * 1000L);
        }
    }

    /**
     * Hủy bỏ bộ đếm thời gian khi người chơi hoàn thành lượt đúng hạn.
     *
     * @param roomId Mã phòng chơi
     */
    public void cancelTurnTimer(String roomId) {

        if (roomId == null) {
            return;
        }

        roomDeadlines.remove(roomId);
        // Lấy timer khỏi map.
        ScheduledFuture<?> future = roomTimers.remove(roomId);

        if (future != null) {
            // Hủy timer cũ.
            future.cancel(true);
        }
    }

    /**
     * Lấy mốc thời gian hết hạn lượt hiện tại (epoch milliseconds) của phòng.
     *
     * @param roomId Mã phòng chơi
     * @return Epoch ms hoặc 0L nếu không có bộ đếm đang chạy
     */
    public long getTurnDeadlineEpochMs(String roomId) {
        if (roomId == null) {
            return 0L;
        }
        Long deadline = roomDeadlines.get(roomId);
        return deadline != null ? deadline : 0L;
    }

    /**
     * Trả về thời lượng tối đa mỗi lượt (giây).
     */
    public int getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    /**
     * Hủy toàn bộ bộ đếm đang chạy và dừng scheduler khi tắt server.
     */
    public synchronized void shutdown() {
        for (ScheduledFuture<?> future : roomTimers.values()) {
            if (future != null) {
                future.cancel(true);
            }
        }
        roomTimers.clear();
        roomDeadlines.clear();
        scheduler.shutdownNow();
    }

    /**
     * Xử lý khi người chơi hết thời gian.
     */
    private void handleTimeout(
            String roomId,
            String playerId,
            int turnId,
            GameEngine engine,
            AtomicReference<ScheduledFuture<?>> reference) {

        GameRoom room = engine.getRoom();

        boolean timeoutAccepted = false;

        synchronized (room) {

            ScheduledFuture<?> future = reference.get();

            // Kiểm tra timer có còn hợp lệ không.
            if (roomTimers.get(roomId) != future) {
                return;
            }

            Player currentPlayer = room.getCurrentPlayer();

            // Kiểm tra phòng, người chơi và turnId.
            if (room.getStatus() != RoomStatus.PLAYING
                    || room.getTurnId() != turnId
                    || currentPlayer == null
                    || !currentPlayer.getId().equals(playerId)) {

                if (roomTimers.remove(roomId, future)) {
                    roomDeadlines.remove(roomId);
                }
                return;
            }

            // Xóa timer đã hết hạn trước khi gọi engine ngoài synchronized (room).
            if (roomTimers.remove(roomId, future)) {
                roomDeadlines.remove(roomId);
            }
        }

        // Chuyển trạng thái lượt sang TIMEOUT (không giữ khóa room khi gọi engine để tránh đảo ngược khóa).
        ActionResult result = engine.handleTimeout(
                roomId,
                playerId,
                turnId,
                "TIMEOUT-" + UUID.randomUUID()
        );
        timeoutAccepted = result.isSuccess();

        /*
         * 4. Chỉ gọi Bot khi timeout hợp lệ.
         * Không giữ khóa GameRoom trong lúc Bot chạy,
         * để người chơi thật vẫn có thể giành lại quyền.
         */
        if (timeoutAccepted) {
            try {
                BotController.processBotTurn(engine);
            } catch (Throwable ignored) {
            } finally {
                engine.ensureBotTurnResolved(roomId, turnId);
                if (room.getStatus() == RoomStatus.PLAYING) {
                    startTurnTimer(
                            roomId,
                            room.getTurnId(),
                            engine
                    );
                }
            }
        }
    }

    private void handleDedicatedBotTurn(
            String roomId,
            String playerId,
            int turnId,
            GameEngine engine,
            AtomicReference<ScheduledFuture<?>> reference) {
        GameRoom room = engine.getRoom();
        synchronized (room) {
            ScheduledFuture<?> future = reference.get();
            if (roomTimers.get(roomId) != future) {
                return;
            }
            Player currentPlayer = room.getCurrentPlayer();
            if (room.getStatus() != RoomStatus.PLAYING
                    || room.getTurnId() != turnId
                    || currentPlayer == null
                    || !currentPlayer.getId().equals(playerId)
                    || !currentPlayer.isBot()) {
                if (roomTimers.remove(roomId, future)) {
                    roomDeadlines.remove(roomId);
                }
                return;
            }
            if (roomTimers.remove(roomId, future)) {
                roomDeadlines.remove(roomId);
            }
        }
        try {
            BotController.processBotTurn(engine);
        } catch (Throwable ignored) {
        } finally {
            engine.ensureBotTurnResolved(roomId, turnId);
            if (room.getStatus() == RoomStatus.PLAYING) {
                startTurnTimer(roomId, room.getTurnId(), engine);
            }
        }
    }
}
