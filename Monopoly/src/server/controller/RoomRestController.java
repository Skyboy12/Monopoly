package server.controller;

import network.GameMessage;
import server.service.RoomManagerService;

/**
 * Controller xử lý các REST API quản lý phòng chờ và sảnh game (Lobby Management).
 * Tham khảo docs/workflow.md Mục 4.2 & docs/member-4-backend-platform.md:
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI (REST API ENDPOINTS - Base URL: /api/v1):
 * ════════════════════════════════════════════════════════════════════════════════
 * 1. GET /api/v1/rooms:
 *    - Lấy danh sách các phòng chờ hiện có.
 *    - Chuyển đổi GameRoom thành RoomDTO (không expose trực tiếp domain object).
 *
 * 2. POST /api/v1/rooms:
 *    - Payload: { "roomName": "...", "maxPlayers": 4 }
 *    - Tạo phòng chơi mới, đặt host là người chơi tạo phòng.
 *
 * 3. POST /api/v1/rooms/{roomId}/join:
 *    - Người chơi tham gia vào phòng chờ (kiểm tra phòng chưa đầy và đang ở trạng thái WAITING).
 *
 * 4. POST /api/v1/rooms/{roomId}/leave:
 *    - Người chơi rời khỏi phòng chờ.
 *
 * 5. POST /api/v1/rooms/{roomId}/start:
 *    - Chỉ Host mới có quyền gọi. Yêu cầu tối thiểu 2 người chơi để bắt đầu ván đấu.
 */
public class RoomRestController {
    private final RoomManagerService roomManagerService;

    public RoomRestController(RoomManagerService roomManagerService) {
        this.roomManagerService = roomManagerService;
    }

    /**
     * API: Lấy danh sách phòng chờ.
     */
    public GameMessage getRooms() {
        // TODO: Truy vấn toàn bộ phòng từ roomManagerService và đóng gói thành GameMessage
        return null;
    }

    /**
     * API: Tạo phòng chơi mới.
     */
    public GameMessage createRoom(String roomName, int maxPlayers, String hostUserId) {
        // TODO: Khởi tạo phòng mới qua roomManagerService, thêm host vào phòng và trả về kết quả
        return null;
    }

    /**
     * API: Tham gia vào phòng chơi.
     */
    public GameMessage joinRoom(String roomId, String userId, String userName) {
        // TODO: Tìm phòng theo roomId, kiểm tra sức chứa và thêm Player mới vào phòng
        return null;
    }

    /**
     * API: Rời khỏi phòng chơi.
     */
    public GameMessage leaveRoom(String roomId, String userId) {
        // TODO: Xóa người chơi khỏi phòng
        return null;
    }

    /**
     * API: Bắt đầu ván đấu (Chỉ Host được gọi).
     */
    public GameMessage startGame(String roomId, String hostUserId) {
        // TODO: Kiểm tra quyền Host, kiểm tra số người chơi >= 2 và kích hoạt gameEngine.startGame()
        return null;
    }
}

