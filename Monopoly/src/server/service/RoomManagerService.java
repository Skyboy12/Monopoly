package server.service;

import model.GameRoom;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quản lý các phòng chơi đang hoạt động trên Server (RAM-based State).
 * Tham khảo docs/member-4-backend-platform.md & docs/plan-summary.md:
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI (ROOM CONCURRENCY & SERIALIZATION):
 * ════════════════════════════════════════════════════════════════════════════════
 * - Dùng ConcurrentHashMap<String, GameRoom> để lưu trữ các phòng cờ đang hoạt động.
 * - Concurrency: Mọi thao tác đồng thời phải được serialize độc lập trong từng phòng (GameRoom),
 *   tuyệt đối không dùng synchronized khóa toàn bộ hệ thống server làm nghẽn các phòng khác.
 * - Quản lý vòng đời: createRoom(), getRoomById(), getAllActiveRooms(), removeRoom().
 */
public class RoomManagerService {
    private final ConcurrentHashMap<String, GameRoom> activeRooms;

    public RoomManagerService() {
        this.activeRooms = new ConcurrentHashMap<>();
    }

    /**
     * Tạo một phòng chơi mới.
     *
     * @param roomId     Mã định danh phòng (duy nhất)
     * @param roomName   Tên hiển thị của phòng
     * @param maxPlayers Số người chơi tối đa (từ 2 đến 4 người)
     * @return GameRoom vừa tạo
     */
    public GameRoom createRoom(String roomId, String roomName, int maxPlayers) {
        // TODO: Khởi tạo đối tượng GameRoom mới và lưu vào activeRooms
        return null;
    }

    /**
     * Lấy phòng chơi theo mã phòng.
     */
    public GameRoom getRoom(String roomId) {
        // TODO: Truy vấn GameRoom từ activeRooms theo roomId
        return null;
    }

    /**
     * Lấy danh sách tất cả các phòng đang chờ hoặc đang chơi.
     */
    public List<GameRoom> getAllRooms() {
        // TODO: Trả về danh sách toàn bộ các phòng hiện có trong activeRooms
        return null;
    }

    /**
     * Xóa phòng chơi khi ván đấu kết thúc hoặc phòng bị hủy.
     */
    public boolean deleteRoom(String roomId) {
        // TODO: Xóa GameRoom khỏi activeRooms
        return false;
    }
}

