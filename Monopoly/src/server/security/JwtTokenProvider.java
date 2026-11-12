package server.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Tiện ích xử lý JSON Web Token (JWT) chuẩn HS256 cho REST API và WebSocket handshake.
 */
public class JwtTokenProvider {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final String secretKey;
    private final long validityInMilliseconds;

    public JwtTokenProvider(String secretKey, long validityInMilliseconds) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Secret key không được rỗng.");
        }
        if (validityInMilliseconds <= 0) {
            throw new IllegalArgumentException("Thời gian hiệu lực phải > 0.");
        }
        this.secretKey = secretKey;
        this.validityInMilliseconds = validityInMilliseconds;
    }

    /**
     * Sinh JWT Token khi người chơi đăng nhập thành công.
     */
    public String generateToken(String userId, String username) {
        if (userId == null || userId.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("userId và username không được để trống.");
        }

        String rawToken = stripBearer(userId);
        long now = System.currentTimeMillis();
        long exp = now + validityInMilliseconds;

        String headerEncoded = base64UrlEncode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadJson = String.format(
                "{\"sub\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"iat\":%d,\"exp\":%d}",
                escapeJson(rawToken), escapeJson(rawToken), escapeJson(username), now / 1000, exp / 1000
        );
        String payloadEncoded = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signingInput = headerEncoded + "." + payloadEncoded;
        String signature = sign(signingInput);

        return signingInput + "." + signature;
    }

    /**
     * Xác thực tính hợp lệ của JWT token gửi lên từ client.
     */
    public boolean validateToken(String token) {
        try {
            String raw = stripBearer(token);
            if (raw == null || raw.isBlank()) {
                return false;
            }

            String[] parts = raw.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSig = sign(signingInput);

            if (!MessageDigest.isEqual(
                    expectedSig.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            long expSeconds = extractLongClaim(payloadJson, "exp");
            long nowSeconds = System.currentTimeMillis() / 1000;

            return expSeconds > nowSeconds;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lấy userId từ JWT token.
     */
    public String extractUserId(String token) {
        if (!validateToken(token)) {
            return null;
        }
        try {
            String[] parts = stripBearer(token).split("\\.");
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            String userId = extractStringClaim(payloadJson, "userId");
            return (userId != null) ? userId : extractStringClaim(payloadJson, "sub");
        } catch (Exception e) {
            return null;
        }
    }

    public String extractUsername(String token) {
        if (!validateToken(token)) {
            return null;
        }
        try {
            String[] parts = stripBearer(token).split("\\.");
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            return extractStringClaim(payloadJson, "username");
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể ký JWT token", e);
        }
    }

    private static String stripBearer(String token) {
        if (token == null) return null;
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String str) {
        return Base64.getUrlDecoder().decode(str);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractStringClaim(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static long extractLongClaim(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return -1L;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}