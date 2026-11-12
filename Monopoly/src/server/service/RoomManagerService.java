package server.service;

import engine.GameEngine;
import model.GameRoom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Service quản lý các phòng chơi đang hoạt động trên Server (RAM-based State).
 * Tích hợp trực tiếp với GameEngine và TurnTimerService của Thành viên 3.
 */
public class RoomManagerService {
    private final ConcurrentHashMap<String, GameRoom> activeRooms;
    private final ConcurrentHashMap<String, GameEngine> roomEngines;
    private final ConcurrentHashMap<String, String> roomHosts;
    private final TurnTimerService turnTimerService;

    public RoomManagerService() {
        this(new TurnTimerService(Executors.newScheduledThreadPool(4), 30));
    }

    public RoomManagerService(TurnTimerService turnTimerService) {
        this.activeRooms = new ConcurrentHashMap<>();
        this.roomEngines = new ConcurrentHashMap<>();
        this.roomHosts = new ConcurrentHashMap<>();
        this.turnTimerService = turnTimerService;
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
            if (turnTimerService == null) return;
            GameEngine.EngineEvent.Type type = event.getType();
            if (type == GameEngine.EngineEvent.Type.GAME_STARTED
                    || type == GameEngine.EngineEvent.Type.TURN_ENDED
                    || type == GameEngine.EngineEvent.Type.EXTRA_ROLL_GRANTED
                    || type == GameEngine.EngineEvent.Type.HUMAN_CONTROL_RECLAIMED) {
                turnTimerService.startTurnTimer(newRoom.getId(), newRoom.getTurnId(), engine);
            } else if (type == GameEngine.EngineEvent.Type.GAME_FINISHED) {
                turnTimerService.cancelTurnTimer(newRoom.getId());
            }
        });

        roomEngines.put(roomId, engine);
        return newRoom;
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

    public boolean deleteRoom(String roomId) {
        if (roomId == null) return false;
        if (turnTimerService != null) {
            turnTimerService.cancelTurnTimer(roomId);
        }
        roomEngines.remove(roomId);
        roomHosts.remove(roomId);
        return activeRooms.remove(roomId) != null;
    }
}