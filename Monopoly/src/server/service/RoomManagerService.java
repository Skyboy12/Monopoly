package server.service;

import engine.GameEngine;
import model.GameRoom;
import model.RoomStatus;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Service quản lý các phòng chơi đang hoạt động trên Server (RAM-based State).
 * Tích hợp trực tiếp với GameEngine và TurnTimerService của Thành viên 3.
 */
public class RoomManagerService {
    private static final int MAX_ROOM_LOGS = 30;
    private static final long FINISHED_ROOM_IDLE_MS = 10L * 60L * 1000L; // 10 minutes
    private static final long WAITING_ROOM_IDLE_MS = 30L * 60L * 1000L; // 30 minutes
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ConcurrentHashMap<String, GameRoom> activeRooms;
    private final ConcurrentHashMap<String, GameEngine> roomEngines;
    private final ConcurrentHashMap<String, String> roomHosts;
    private final ConcurrentHashMap<String, Deque<String>> roomLogs;
    private final ConcurrentHashMap<String, Long> roomLastActivityMs;
    private final TurnTimerService turnTimerService;
    private final ScheduledExecutorService cleanupScheduler;
    private volatile BiConsumer<GameRoom, GameEngine.EngineEvent> globalEngineEventListener;

    public RoomManagerService() {
        this(new TurnTimerService(Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "monopoly-turn-timer");
            t.setDaemon(true);
            return t;
        }), 30));
    }

    public RoomManagerService(TurnTimerService turnTimerService) {
        this.activeRooms = new ConcurrentHashMap<>();
        this.roomEngines = new ConcurrentHashMap<>();
        this.roomHosts = new ConcurrentHashMap<>();
        this.roomLogs = new ConcurrentHashMap<>();
        this.roomLastActivityMs = new ConcurrentHashMap<>();
        this.turnTimerService = turnTimerService;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monopoly-room-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(
                createCleanupTask(activeRooms, roomEngines, roomHosts, roomLogs, roomLastActivityMs, turnTimerService),
                60, 60, TimeUnit.SECONDS
        );
    }

    public void setGlobalEngineEventListener(BiConsumer<GameRoom, GameEngine.EngineEvent> listener) {
        this.globalEngineEventListener = listener;
    }

    public GameRoom createRoom(String roomId, String roomName, int maxPlayers) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId không được để trống.");
        }
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("Tên phòng không được để trống.");
        }
        int validMaxPlayers = Math.max(2, Math.min(4, maxPlayers));

        GameRoom newRoom = new GameRoom(roomId, roomName.trim(), validMaxPlayers);
        GameRoom existing = activeRooms.putIfAbsent(roomId, newRoom);
        if (existing != null) {
            throw new IllegalStateException("Phòng với mã " + roomId + " đã tồn tại.");
        }

        // Khởi tạo GameEngine của Thành viên 3 và gắn Listener tự động điều khiển TurnTimerService
        GameEngine engine = new GameEngine(newRoom);
        engine.addEventListener(event -> {
            if (turnTimerService != null) {
                GameEngine.EngineEvent.Type type = event.getType();
                if (type == GameEngine.EngineEvent.Type.GAME_STARTED
                        || type == GameEngine.EngineEvent.Type.TURN_ENDED
                        || type == GameEngine.EngineEvent.Type.EXTRA_ROLL_GRANTED
                        || type == GameEngine.EngineEvent.Type.HUMAN_CONTROL_RECLAIMED) {
                    turnTimerService.startTurnTimer(newRoom.getId(), newRoom.getTurnId(), engine);
                } else if (type == GameEngine.EngineEvent.Type.GAME_FINISHED) {
                    turnTimerService.cancelTurnTimer(newRoom.getId());
                }
            }
            BiConsumer<GameRoom, GameEngine.EngineEvent> listener = this.globalEngineEventListener;
            if (listener != null) {
                try {
                    listener.accept(newRoom, event);
                } catch (RuntimeException ignored) {
                }
            }
        });

        roomEngines.put(roomId, engine);
        touchRoom(roomId);
        return newRoom;
    }

    public void touchRoom(String roomId) {
        if (roomId != null) {
            roomLastActivityMs.put(roomId, System.currentTimeMillis());
        }
    }

    public int getTurnTimeoutSeconds() {
        return turnTimerService != null ? turnTimerService.getTurnTimeoutSeconds() : 30;
    }

    public GameRoom createRoomWithHost(String roomId, String roomName, int maxPlayers, String hostUserId) {
        GameRoom room = createRoom(roomId, roomName, maxPlayers);
        if (hostUserId != null && !hostUserId.isBlank()) {
            roomHosts.put(roomId, hostUserId);
        }
        return room;
    }

    public GameRoom getRoom(String roomId) {
        return roomId == null ? null : activeRooms.get(roomId);
    }

    public GameEngine getEngine(String roomId) {
        return roomId == null ? null : roomEngines.get(roomId);
    }

    public String getHostUserId(String roomId) {
        return roomId == null ? null : roomHosts.get(roomId);
    }

    public void setHostUserId(String roomId, String hostUserId) {
        if (roomId != null && hostUserId != null) {
            roomHosts.put(roomId, hostUserId);
        }
    }

    public List<GameRoom> getAllRooms() {
        return Collections.unmodifiableList(new ArrayList<>(activeRooms.values()));
    }

    public long getTurnDeadlineEpochMs(String roomId) {
        if (turnTimerService == null || roomId == null) {
            return 0L;
        }
        return turnTimerService.getTurnDeadlineEpochMs(roomId);
    }

    public void appendRoomLog(String roomId, String message) {
        if (roomId == null || roomId.isBlank() || message == null || message.isBlank()) {
            return;
        }
        touchRoom(roomId);
        String entry = "[" + LocalTime.now().format(LOG_TIME_FORMATTER) + "] " + message.trim();
        Deque<String> deque = roomLogs.computeIfAbsent(roomId, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addFirst(entry);
            while (deque.size() > MAX_ROOM_LOGS) {
                deque.removeLast();
            }
        }
    }

    public List<String> getRoomLogs(String roomId) {
        if (roomId == null) {
            return List.of();
        }
        Deque<String> deque = roomLogs.get(roomId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }

    public boolean deleteRoom(String roomId) {
        if (roomId == null) return false;
        if (turnTimerService != null) {
            turnTimerService.cancelTurnTimer(roomId);
        }
        roomEngines.remove(roomId);
        roomHosts.remove(roomId);
        roomLogs.remove(roomId);
        roomLastActivityMs.remove(roomId);
        return activeRooms.remove(roomId) != null;
    }

    private static Runnable createCleanupTask(
            ConcurrentHashMap<String, GameRoom> activeRooms,
            ConcurrentHashMap<String, GameEngine> roomEngines,
            ConcurrentHashMap<String, String> roomHosts,
            ConcurrentHashMap<String, Deque<String>> roomLogs,
            ConcurrentHashMap<String, Long> roomLastActivityMs,
            TurnTimerService turnTimerService) {
        return () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, GameRoom> entry : activeRooms.entrySet()) {
                String roomId = entry.getKey();
                GameRoom room = entry.getValue();
                if (room == null) {
                    continue;
                }
                Long lastAct = roomLastActivityMs.get(roomId);
                if (lastAct == null) {
                    roomLastActivityMs.putIfAbsent(roomId, now);
                    continue;
                }
                long idleMs = now - lastAct;
                RoomStatus status = room.getStatus();
                if ((status == RoomStatus.FINISHED && idleMs > FINISHED_ROOM_IDLE_MS)
                        || (status == RoomStatus.WAITING && idleMs > WAITING_ROOM_IDLE_MS)) {
                    if (turnTimerService != null) {
                        turnTimerService.cancelTurnTimer(roomId);
                    }
                    roomEngines.remove(roomId);
                    roomHosts.remove(roomId);
                    roomLogs.remove(roomId);
                    roomLastActivityMs.remove(roomId);
                    activeRooms.remove(roomId);
                }
            }
        };
    }

    public void shutdown() {
        cleanupScheduler.shutdownNow();
        if (turnTimerService != null) {
            turnTimerService.shutdown();
        }
    }
}