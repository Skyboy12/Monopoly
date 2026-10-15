package server.security;

/**
 * Bộ lọc bảo mật Origin Shielding (Cloudflare Origin Filter).
 * Tham khảo từ docs/cloudflare-worker-firewall.md &
 * docs/member-4-backend-platform.md:
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI (ORIGIN SHIELDING & ANTI-BYPASS):
 * ════════════════════════════════════════════════════════════════════════════════
 * - Mọi request đi từ Internet bắt buộc phải đi qua Cloudflare Worker (Edge
 * Firewall).
 * - Cloudflare Worker sẽ tự động gắn thêm Header bí mật: "X-Origin-Verify:
 * <SECRET_KEY>".
 * - Khi request tới máy chủ backend, filter này sẽ kiểm tra header
 * "X-Origin-Verify":
 * 1. Nếu header bị thiếu hoặc giá trị không khớp bí mật -> DROP NGAY LẬP TỨC
 * (HTTP 403 Forbidden).
 * Điều này ngăn chặn triệt để hacker dò quét IP mạng nhà để bypass tường lửa
 * Cloudflare.
 * 2. Nếu hợp lệ -> cho phép request đi tiếp vào Controller / WebSocket.
 */
public class CloudflareOriginFilter {
    public static final String ORIGIN_HEADER_NAME = "X-Origin-Verify";
    private final String expectedSecretKey;

    public CloudflareOriginFilter(String expectedSecretKey) {
        this.expectedSecretKey = expectedSecretKey;
    }

    /**
     * Xác thực header bí mật từ request chuyển tiếp của Cloudflare Worker.
     *
     * @param headerValue Giá trị của header "X-Origin-Verify" trong HTTP Request
     * @return true nếu khớp khóa bí mật, ngược lại false
     */
    public boolean verifyOriginHeader(String headerValue) {
        // TODO: Kiểm tra headerValue != null và khớp chính xác với expectedSecretKey
        return false;
    }
}
