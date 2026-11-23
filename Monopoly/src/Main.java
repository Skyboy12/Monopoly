import server.controller.AuthRestController;
import server.controller.GameWebSocketController;
import server.controller.RoomRestController;
import server.database.Connector;
import server.gateway.ApiGatewayServer;
import server.gateway.GatewayConfig;
import server.persistence.UserRepository;
import server.security.CloudflareOriginFilter;
import server.security.JwtTokenProvider;
import server.security.PasswordEncoderUtil;
import server.service.RoomManagerService;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Entry point chính của dự án Monopoly Backend.
 */
public class Main {
    public static void main(String[] args) {
        GatewayConfig config = GatewayConfig.fromArgsAndEnv(args);

        // 1. Khởi tạo các thành phần Security & Persistence
        CloudflareOriginFilter originFilter = CloudflareOriginFilter.forEnvironment(
                config.getMode(),
                config.getOriginSecret()
        );
        JwtTokenProvider jwtProvider = new JwtTokenProvider(
                config.getJwtSecret(),
                config.getJwtValidityMs()
        );
        PasswordEncoderUtil passwordEncoder = new PasswordEncoderUtil();

        UserRepository userRepository;
        if (Connector.isConfigured()) {
            try {
                Connector.initSchema();
                if (!Connector.testConnection()) {
                    throw new SQLException("SELECT 1 verification failed");
                }
                userRepository = UserRepository.withMySql();
                System.out.println("Database: MySQL CONNECTED (" + Connector.getSafeConnectionSummary() + ")");
            } catch (SQLException e) {
                if ("prod".equals(config.getMode())) {
                    System.err.println("Không thể kết nối cơ sở dữ liệu MySQL (" + Connector.getSafeConnectionSummary() + "): " + e.getMessage());
                    System.exit(1);
                    return;
                }
                System.err.println("Cảnh báo: Không kết nối được MySQL (" + e.getMessage() + "), chuyển sang In-Memory fallback trong dev mode.");
                userRepository = new UserRepository();
            }
        } else {
            userRepository = new UserRepository();
            System.out.println("Database: IN-MEMORY (chưa cấu hình biến DATABASE)");
        }

        // 2. Khởi tạo Services & Controllers
        RoomManagerService roomManager = new RoomManagerService();
        AuthRestController authController = new AuthRestController(userRepository, passwordEncoder, jwtProvider);
        RoomRestController roomController = new RoomRestController(roomManager);
        GameWebSocketController wsController = new GameWebSocketController();

        // 3. Khởi tạo & chạy API Gateway hợp nhất (HTTP/1.1 + WebSocket STOMP 1.2)
        ApiGatewayServer gateway = new ApiGatewayServer(
                config,
                originFilter,
                jwtProvider,
                userRepository,
                roomManager,
                authController,
                roomController,
                wsController
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gateway.stop();
            roomManager.shutdown();
        }, "monopoly-shutdown-hook"));

        try {
            gateway.start();
        } catch (IOException e) {
            System.err.println("Không thể khởi động API Gateway trên cổng " + config.getPort() + ": " + e.getMessage());
            roomManager.shutdown();
            System.exit(1);
            return;
        }

        int port = gateway.getPort();
        System.out.println("=== MONOPOLY API GATEWAY ĐÃ KHỞI ĐỘNG ===");
        System.out.println("Port: " + port);
        System.out.println("Mode: " + config.getMode()
                + " (Cloudflare Origin Shielding: "
                + (originFilter.isProtectionEnabled() ? "ENABLED" : "BYPASS") + ")");
        System.out.println("REST Endpoints:");
        System.out.println("  GET  http://localhost:" + port + "/health");
        System.out.println("  POST http://localhost:" + port + "/api/v1/auth/register");
        System.out.println("  POST http://localhost:" + port + "/api/v1/auth/login");
        System.out.println("  GET  http://localhost:" + port + "/api/v1/auth/me");
        System.out.println("  GET  http://localhost:" + port + "/api/v1/rooms");
        System.out.println("  POST http://localhost:" + port + "/api/v1/rooms");
        System.out.println("  GET  http://localhost:" + port + "/api/v1/rooms/{roomId}");
        System.out.println("  POST http://localhost:" + port + "/api/v1/rooms/{roomId}/join");
        System.out.println("  POST http://localhost:" + port + "/api/v1/rooms/{roomId}/leave");
        System.out.println("  POST http://localhost:" + port + "/api/v1/rooms/{roomId}/start");
        System.out.println("WebSocket STOMP Endpoint:");
        System.out.println("  WS   ws://localhost:" + port + "/ws");
    }
}
