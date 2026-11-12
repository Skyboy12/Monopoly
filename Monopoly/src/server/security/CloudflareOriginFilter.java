package server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bộ lọc bảo mật Origin Shielding (Cloudflare Origin Filter).
 * Chặn các request bypass Cloudflare Worker đi thẳng vào IP server.
 */
public class CloudflareOriginFilter {
    public static final String ORIGIN_HEADER_NAME = "X-Origin-Verify";
    private final String expectedSecretKey;
    private final boolean protectionEnabled;

    public CloudflareOriginFilter(String expectedSecretKey) {
        this(expectedSecretKey, true);
    }

    public CloudflareOriginFilter(String expectedSecretKey, boolean protectionEnabled) {
        this.expectedSecretKey = expectedSecretKey;
        this.protectionEnabled = protectionEnabled;
    }

    /**
     * Xác thực header bí mật từ request chuyển tiếp của Cloudflare Worker.
     *
     * @param headerValue Giá trị của header "X-Origin-Verify" trong HTTP Request
     * @return true nếu khớp khóa bí mật (hoặc đang tắt bảo vệ ở môi trường local), ngược lại false
     */
    public boolean verifyOriginHeader(String headerValue) {
        if (!protectionEnabled) {
            return true;
        }
        if (expectedSecretKey == null || expectedSecretKey.isBlank() || headerValue == null) {
            return false;
        }
        // So sánh constant-time tránh dò khóa bí mật
        return MessageDigest.isEqual(
                expectedSecretKey.getBytes(StandardCharsets.UTF_8),
                headerValue.getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean isProtectionEnabled() {
        return protectionEnabled;
    }
}