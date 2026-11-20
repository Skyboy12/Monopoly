import server.controller.AuthRestController;
import server.controller.GameWebSocketController;
import server.controller.RoomRestController;
import server.persistence.UserRepository;
import server.security.CloudflareOriginFilter;
import server.security.JwtTokenProvider;
import server.security.PasswordEncoderUtil;
import server.service.RoomManagerService;

/**
 * Entry point chính của dự án Monopoly Backend.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== KHỞI ĐỘNG HỆ THỐNG BACKEND MONOPOLY ===");

        // 1. Khởi tạo các thành phần Security & Persistence
        CloudflareOriginFilter originFilter = CloudflareOriginFilter.forEnvironment("dev", null);
        JwtTokenProvider jwtProvider = new JwtTokenProvider("MonopolyJwtSecretKey2026PtitOopProject", 3600_000L);
        PasswordEncoderUtil passwordEncoder = new PasswordEncoderUtil();
        UserRepository userRepository = new UserRepository();

        // 2. Khởi tạo Services & Controllers
        RoomManagerService roomManager = new RoomManagerService();
        AuthRestController authController = new AuthRestController(userRepository, passwordEncoder, jwtProvider);
        RoomRestController roomController = new RoomRestController(roomManager);
        GameWebSocketController wsController = new GameWebSocketController();

        System.out.println("Hệ thống Backend Monopoly đã sẵn sàng.");
    }
}
