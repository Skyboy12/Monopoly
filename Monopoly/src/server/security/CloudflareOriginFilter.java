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
     * Khởi tạo bộ lọc cho chế độ dev (chạy local, bypass Cloudflare).
     *
     * @return CloudflareOriginFilter với protectionEnabled = false
     */
    public static CloudflareOriginFilter devMode() {
        return new CloudflareOriginFilter(null, false);
    }

    /**
     * Khởi tạo bộ lọc cho chế độ prod (yêu cầu request đi qua Cloudflare).
     *
     * @param expectedSecretKey Khóa bí mật X-Origin-Verify khớp với Cloudflare Worker
     * @return CloudflareOriginFilter với protectionEnabled = true
     */
    public static CloudflareOriginFilter prodMode(String expectedSecretKey) {
        return new CloudflareOriginFilter(expectedSecretKey, true);
    }

    /**
     * Khởi tạo bộ lọc theo môi trường chạy ("dev" hoặc "prod").
     *
     * @param mode              Chế độ chạy ("dev"/"local" để bypass Cloudflare, "prod"/"production" để yêu cầu Cloudflare)
     * @param expectedSecretKey Khóa bí mật X-Origin-Verify dùng trong prod mode
     * @return CloudflareOriginFilter tương ứng với môi trường
     * @throws IllegalArgumentException nếu mode null hoặc không hợp lệ
     */
    public static CloudflareOriginFilter forEnvironment(String mode, String expectedSecretKey) {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("Environment mode must not be null or blank");
        }
        String normalized = mode.trim().toLowerCase();
        if ("dev".equals(normalized) || "development".equals(normalized) || "local".equals(normalized)) {
            return devMode();
        }
        if ("prod".equals(normalized) || "production".equals(normalized)) {
            return prodMode(expectedSecretKey);
        }
        throw new IllegalArgumentException("Unsupported environment mode: " + mode);
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

    public String getExpectedSecretKey() {
        return expectedSecretKey;
    }
}