import engine.GameEngine;
import model.GameRoom;
import model.Player;
import model.tile.PropertyTile;
import model.tile.Tile;
import network.GameMessage;
import server.controller.AuthRestController;
import server.controller.GameWebSocketController;
import server.controller.RoomRestController;
import server.persistence.UserRepository;
import server.security.CloudflareOriginFilter;
import server.security.JwtTokenProvider;
import server.security.PasswordEncoderUtil;
import server.service.RoomManagerService;

/**
 * Entry point chính của dự án Monopoly & Bộ kiểm thử tích hợp Backend (Thành viên 4).
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== KHỞI ĐỘNG HỆ THỐNG BACKEND MONOPOLY & INTEGRATION TESTS ===");

        // 1. Khởi tạo các thành phần Security & Persistence
        CloudflareOriginFilter originFilter = new CloudflareOriginFilter("my-secret-origin-key-32-characters", true);
        JwtTokenProvider jwtProvider = new JwtTokenProvider("MonopolyJwtSecretKey2026PtitOopProject", 3600_000L);
        PasswordEncoderUtil passwordEncoder = new PasswordEncoderUtil();
        UserRepository userRepository = new UserRepository();

        // 2. Khởi tạo Services & Controllers
        RoomManagerService roomManager = new RoomManagerService();
        AuthRestController authController = new AuthRestController(userRepository, passwordEncoder, jwtProvider);
        RoomRestController roomController = new RoomRestController(roomManager);
        GameWebSocketController wsController = new GameWebSocketController();

        // --- TEST 1: Kiểm tra CloudflareOriginFilter (Origin Shielding) ---
        assertCondition(!originFilter.verifyOriginHeader(null), "Phải chặn request thiếu X-Origin-Verify");
        assertCondition(!originFilter.verifyOriginHeader("wrong-key"), "Phải chặn request sai X-Origin-Verify");
        assertCondition(originFilter.verifyOriginHeader("my-secret-origin-key-32-characters"), "Phải cho phép X-Origin-Verify hợp lệ");
        System.out.println("[PASS] Test 1: CloudflareOriginFilter chặn bypass IP trực tiếp thành công.");

        // --- TEST 2: Đăng ký, Đăng nhập, Mã hóa mật khẩu và JWT ---
        GameMessage reg1 = authController.register("huy_ptit", "password123", "Hữu Huy");
        GameMessage reg2 = authController.register("khanh_ptit", "password456", "Duy Khánh");
        assertCondition("AUTH_REGISTERED".equals(reg1.getType()), "Đăng ký user 1 phải thành công");
        assertCondition("AUTH_REGISTERED".equals(reg2.getType()), "Đăng ký user 2 phải thành công");

        String user1Id = reg1.getSenderId();
        String user2Id = reg2.getSenderId();
        String token1 = jwtProvider.generateToken(user1Id, "huy_ptit");
        assertCondition(jwtProvider.validateToken("Bearer " + token1), "JWT Token phải hợp lệ");
        assertCondition(user1Id.equals(jwtProvider.extractUserId("Bearer " + token1)), "Phải trích xuất đúng userId từ JWT");
        System.out.println("[PASS] Test 2: Auth, Salted Password Hash & JWT HS256 hoạt động chính xác.");

        // --- TEST 3: REST Lobby (Tạo phòng, Tham gia, Phân quyền Host) ---
        GameMessage createMsg = roomController.createRoom("Phòng Đại Gia Hà Đông", 4, user1Id);
        assertCondition("ROOM_CREATED".equals(createMsg.getType()), "Tạo phòng phải thành công");
        String roomId = createMsg.getRoomId();

        GameMessage joinMsg = roomController.joinRoom(roomId, user2Id, "Duy Khánh");
        assertCondition("ROOM_JOINED".equals(joinMsg.getType()), "User 2 tham gia phòng phải thành công");

        // User 2 (không phải Host) cố tình bấm Start Game -> Phải bị từ chối
        GameMessage nonHostStart = roomController.startGame(roomId, user2Id);
        assertCondition("ERROR".equals(nonHostStart.getType()) && nonHostStart.getPayload().contains("FORBIDDEN_NOT_HOST"),
                "Người không phải Host không được phép bắt đầu ván đấu");

        // Host (User 1) bắt đầu ván đấu -> Thành công
        GameMessage hostStart = roomController.startGame(roomId, user1Id);
        assertCondition("GAME_STARTED".equals(hostStart.getType()), "Host bắt đầu ván đấu phải thành công");
        System.out.println("[PASS] Test 3: Quản lý phòng chờ & Phân quyền Host chuẩn xác.");

        // --- TEST 4: WebSocket Authoritative Validation & Anti-Cheat ---
        GameEngine engine = roomManager.getEngine(roomId);
        GameRoom room = roomManager.getRoom(roomId);
        int currentTurnId = room.getTurnId();

        // 4.1: Sai người chơi (User 2 cố tình gieo xúc xắc trong lượt của User 1)
        GameMessage wrongTurnMsg = wsController.handleRollDice(roomId, user2Id, currentTurnId, engine);
        assertCondition("ERROR".equals(wrongTurnMsg.getType()) && wrongTurnMsg.getPayload().contains("NOT_YOUR_TURN"),
                "Phải từ chối khi không phải lượt của người chơi");

        // 4.2: Sai turnId (Stale / Replay command)
        GameMessage staleTurnMsg = wsController.handleRollDice(roomId, user1Id, currentTurnId + 99, engine);
        assertCondition("ERROR".equals(staleTurnMsg.getType()) && staleTurnMsg.getPayload().contains("STALE_TURN"),
                "Phải từ chối khi gửi sai turnId");

        // 4.3: Sai Phase (Chưa gieo xúc xắc đã bấm mua đất)
        GameMessage wrongPhaseMsg = wsController.handleBuyProperty(roomId, user1Id, currentTurnId, 1, engine);
        assertCondition("ERROR".equals(wrongPhaseMsg.getType()), "Phải từ chối mua đất khi chưa gieo xúc xắc");
        System.out.println("[PASS] Test 4: Chặn thành công các hành vi sai lượt, sai turnId và sai Phase.");

        // --- TEST 5: Luồng Gieo xúc xắc, Mua đất và Đồng bộ Snapshot ---
        GameMessage rollOk = wsController.handleRollDice(roomId, user1Id, currentTurnId, engine);
        assertCondition("DICE_ROLLED".equals(rollOk.getType()), "User 1 gieo xúc xắc hợp lệ phải thành công");

        Player p1 = room.getCurrentPlayer();
        Tile landedTile = room.getBoard().getTile(p1.getPosition());
        if (landedTile instanceof PropertyTile && !((PropertyTile) landedTile).hasOwner()) {
            // Thử gửi sai vị trí ô đất so với vị trí thật trên server -> Phải bị chặn
            int fakeTileIndex = (p1.getPosition() + 1) % 40;
            GameMessage cheatTileMsg = wsController.handleBuyProperty(roomId, user1Id, currentTurnId, fakeTileIndex, engine);
            assertCondition("ERROR".equals(cheatTileMsg.getType()) && cheatTileMsg.getPayload().contains("TILE_MISMATCH"),
                    "Phải chặn khi client gửi tileIndex giả mạo");

            // Gửi đúng vị trí ô đất hiện tại -> Mua thành công
            GameMessage buyOk = wsController.handleBuyProperty(roomId, user1Id, currentTurnId, p1.getPosition(), engine);
            assertCondition("PROPERTY_BOUGHT".equals(buyOk.getType()), "Mua tài sản hợp lệ phải thành công");
        }

        // Kết thúc lượt và kiểm tra Snapshot
        GameMessage endTurnMsg = wsController.handleEndTurn(roomId, user1Id, currentTurnId, engine);
        assertCondition("TURN_CHANGED".equals(endTurnMsg.getType()), "Kết thúc lượt phải thành công");

        GameMessage snapshot = wsController.buildSnapshotMessage(engine);
        assertCondition("GAME_STATE_SNAPSHOT".equals(snapshot.getType()), "Phải tạo được Snapshot nhất quán cho các client");
        System.out.println("[PASS] Test 5: Luồng Gieo xúc xắc, Mua đất, Chuyển lượt và Snapshot hoạt động trơn tru.");

        System.out.println("=== TẤT CẢ CÁC BÀI KIỂM THỬ THÀNH VIÊN 4 ĐỀU VƯỢT QUA (100% PASS) ===");

        // Dừng chương trình (đóng thread pool của TurnTimerService)
        System.exit(0);
    }

    // Phương thức phụ trợ nằm NGOÀI hàm main, TRONG class Main
    private static void assertCondition(boolean condition, String failMessage) {
        if (!condition) {
            throw new AssertionError("KIỂM THỬ THẤT BẠI: " + failMessage);
        }
    }
}