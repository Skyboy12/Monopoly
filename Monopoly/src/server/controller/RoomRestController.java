package server.controller;

import engine.GameEngine;
import engine.GameEngine.ActionResult;
import model.GameRoom;
import model.Player;
import model.RoomStatus;
import network.GameMessage;
import server.service.RoomManagerService;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các REST API quản lý phòng chờ và sảnh game (Lobby Management).
 */
public class RoomRestController {
    private static final int DEFAULT_INITIAL_MONEY = 1500;
    private final RoomManagerService roomManagerService;

    public RoomRestController(RoomManagerService roomManagerService) {
        if (roomManagerService == null) {
            throw new IllegalArgumentException("RoomManagerService không được null.");
        }
        this.roomManagerService = roomManagerService;
    }

    /**
     * API 1: GET /api/v1/rooms - Lấy danh sách phòng chờ dưới dạng DTO JSON.
     */
    public GameMessage getRooms() {
        List<GameRoom> rooms = roomManagerService.getAllRooms();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rooms.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toRoomSummaryDtoJson(rooms.get(i)));
        }
        sb.append("]");
        return new GameMessage("ROOM_LIST", "SERVER", null, 0, sb.toString());
    }

    /**
     * API 2: POST /api/v1/rooms - Tạo phòng chơi mới và tự động thêm Host vào phòng.
     */
    public GameMessage createRoom(String roomName, int maxPlayers, String hostUserId) {
        if (hostUserId == null || hostUserId.isBlank()) {
            return errorMessage(null, null, 0, "UNAUTHORIZED", "Thiếu thông tin người tạo phòng.");
        }
        if (roomName == null || roomName.isBlank()) {
            return errorMessage(null, hostUserId, 0, "INVALID_ROOM_NAME", "Tên phòng không được để trống.");
        }
        if (maxPlayers < 2 || maxPlayers > 4) {
            return errorMessage(null, hostUserId, 0, "INVALID_MAX_PLAYERS", "Số người chơi tối đa phải từ 2 đến 4.");
        }

        String roomId = "room-" + UUID.randomUUID().toString().substring(0, 8);
        GameRoom room = roomManagerService.createRoomWithHost(roomId, roomName.trim(), maxPlayers, hostUserId);

        Player hostPlayer = new Player(hostUserId, hostUserId, DEFAULT_INITIAL_MONEY);
        room.addPlayer(hostPlayer);

        return new GameMessage(
                "ROOM_CREATED",
                hostUserId,
                room.getId(),
                room.getTurnId(),
                toRoomSummaryDtoJson(room)
        );
    }

    /**
     * API 3: POST /api/v1/rooms/{roomId}/join - Tham gia vào phòng chơi.
     */
    public GameMessage joinRoom(String roomId, String userId, String userName) {
        if (userId == null || userId.isBlank()) {
            return errorMessage(roomId, null, 0, "INVALID_USER", "userId không hợp lệ.");
        }
        GameRoom room = roomManagerService.getRoom(roomId);
        if (room == null) {
            return errorMessage(roomId, userId, 0, "ROOM_NOT_FOUND", "Không tìm thấy phòng chơi.");
        }

        synchronized (room) {
            if (room.getStatus() != RoomStatus.WAITING) {
                return errorMessage(roomId, userId, room.getTurnId(), "ROOM_NOT_WAITING", "Phòng đã bắt đầu hoặc kết thúc.");
            }
            if (room.getPlayers().size() >= room.getMaxPlayers()) {
                return errorMessage(roomId, userId, room.getTurnId(), "ROOM_FULL", "Phòng đã đầy.");
            }

            String displayName = (userName == null || userName.isBlank()) ? userId : userName.trim();
            Player newPlayer = new Player(userId, displayName, DEFAULT_INITIAL_MONEY);
            boolean added = room.addPlayer(newPlayer);
            if (!added) {
                return errorMessage(roomId, userId, room.getTurnId(), "ALREADY_IN_ROOM", "Người chơi đã có trong phòng.");
            }

            if (roomManagerService.getHostUserId(roomId) == null) {
                roomManagerService.setHostUserId(roomId, userId);
            }

            return new GameMessage(
                    "ROOM_JOINED",
                    userId,
                    room.getId(),
                    room.getTurnId(),
                    toRoomSummaryDtoJson(room)
            );
        }
    }

    /**
     * API 4: POST /api/v1/rooms/{roomId}/leave - Rời khỏi phòng chơi.
     */
    public GameMessage leaveRoom(String roomId, String userId) {
        GameRoom room = roomManagerService.getRoom(roomId);
        if (room == null) {
            return errorMessage(roomId, userId, 0, "ROOM_NOT_FOUND", "Không tìm thấy phòng chơi.");
        }

        synchronized (room) {
            boolean removed = room.removePlayer(userId);
            if (!removed) {
                return errorMessage(roomId, userId, room.getTurnId(), "PLAYER_NOT_IN_ROOM", "Người chơi không có trong phòng.");
            }

            // Nếu phòng trống hoàn toàn -> xóa phòng khỏi bộ nhớ
            if (room.getPlayers().isEmpty()) {
                roomManagerService.deleteRoom(roomId);
                return new GameMessage("ROOM_DELETED", userId, roomId, 0, "{\"deleted\":true}");
            }

            // Nếu Host rời phòng -> nhượng quyền Host cho người kế tiếp
            String currentHost = roomManagerService.getHostUserId(roomId);
            if (userId != null && userId.equals(currentHost)) {
                String nextHost = room.getPlayers().get(0).getId();
                roomManagerService.setHostUserId(roomId, nextHost);
            }

            return new GameMessage(
                    "ROOM_LEFT",
                    userId,
                    room.getId(),
                    room.getTurnId(),
                    toRoomSummaryDtoJson(room)
            );
        }
    }

    /**
     * API 5: POST /api/v1/rooms/{roomId}/start - Bắt đầu ván đấu (Chỉ Host được gọi).
     */
    public GameMessage startGame(String roomId, String hostUserId) {
        GameRoom room = roomManagerService.getRoom(roomId);
        GameEngine engine = roomManagerService.getEngine(roomId);

        if (room == null || engine == null) {
            return errorMessage(roomId, hostUserId, 0, "ROOM_NOT_FOUND", "Không tìm thấy phòng chơi.");
        }

        synchronized (room) {
            String actualHost = roomManagerService.getHostUserId(roomId);
            // Nếu chưa gán host thì người đầu tiên trong danh sách là host
            if (actualHost == null && !room.getPlayers().isEmpty()) {
                actualHost = room.getPlayers().get(0).getId();
                roomManagerService.setHostUserId(roomId, actualHost);
            }

            if (hostUserId == null || !hostUserId.equals(actualHost)) {
                return errorMessage(roomId, hostUserId, room.getTurnId(),
                        "FORBIDDEN_NOT_HOST", "Chỉ chủ phòng mới có quyền bắt đầu ván đấu.");
            }

            ActionResult result = engine.startGame(roomId, "START-" + UUID.randomUUID());
            if (!result.isSuccess()) {
                return errorMessage(roomId, hostUserId, room.getTurnId(), result.getCode(), result.getMessage());
            }

            return new GameMessage(
                    "GAME_STARTED",
                    hostUserId,
                    room.getId(),
                    room.getTurnId(),
                    toRoomSummaryDtoJson(room)
            );
        }
    }

    private String toRoomSummaryDtoJson(GameRoom room) {
        String hostId = roomManagerService.getHostUserId(room.getId());
        if (hostId == null && !room.getPlayers().isEmpty()) {
            hostId = room.getPlayers().get(0).getId();
        }
        return String.format(
                "{\"roomId\":\"%s\",\"roomName\":\"%s\",\"status\":\"%s\",\"currentPlayers\":%d,\"maxPlayers\":%d,\"hostId\":\"%s\",\"turnId\":%d}",
                escape(room.getId()),
                escape(room.getName()),
                room.getStatus().name(),
                room.getPlayers().size(),
                room.getMaxPlayers(),
                escape(hostId == null ? "" : hostId),
                room.getTurnId()
        );
    }

    private GameMessage errorMessage(String roomId, String senderId, int turnId, String code, String message) {
        String payload = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", escape(code), escape(message));
        return new GameMessage("ERROR", senderId, roomId, turnId, payload);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}