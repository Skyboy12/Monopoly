package server.security;

/**
 * Tiện ích xử lý JSON Web Token (JWT) cho xác thực REST API và WebSocket handshake.
 * Tham khảo docs/member-4-backend-platform.md & workflow.md:
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI:
 * ════════════════════════════════════════════════════════════════════════════════
 * - generateToken(userId, username): Sinh chuỗi JWT ký bằng thuật toán HMAC-SHA256 (HS256)
 *   với hạn sử dụng (ví dụ 24h).
 * - validateToken(token): Giải mã, kiểm tra chữ ký và hạn sử dụng của token.
 * - extractUserId(token): Trích xuất userId từ payload (claims) để phục vụ phân quyền.
 */
public class JwtTokenProvider {
    private final String secretKey;
    private final long validityInMilliseconds;

    public JwtTokenProvider(String secretKey, long validityInMilliseconds) {
        this.secretKey = secretKey;
        this.validityInMilliseconds = validityInMilliseconds;
    }

    /**
     * Sinh JWT Token khi người chơi đăng nhập thành công.
     */
    public String generateToken(String userId, String username) {
        // TODO: Đóng gói userId, username vào Claims và ký điện tử với secretKey
        return null;
    }

    /**
     * Xác thực tính hợp lệ của JWT token gửi lên từ client qua header "Authorization: Bearer <token>".
     */
    public boolean validateToken(String token) {
        // TODO: Kiểm tra token hợp lệ, chưa hết hạn và chữ ký đúng
        return false;
    }

    /**
     * Lấy userId từ JWT token.
     */
    public String extractUserId(String token) {
        // TODO: Trích xuất trường userId từ token payload
        return null;
    }
}

