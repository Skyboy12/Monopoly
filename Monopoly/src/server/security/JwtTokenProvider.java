package server.security;

import server.gateway.SimpleJson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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

        String rawUserId = stripBearer(userId);
        long now = System.currentTimeMillis();
        long exp = now + validityInMilliseconds;

        String headerEncoded = base64UrlEncode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", rawUserId);
        claims.put("userId", rawUserId);
        claims.put("username", username);
        claims.put("iat", now / 1000);
        claims.put("exp", exp / 1000);
        String payloadJson = SimpleJson.stringify(claims);
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
            Map<String, Object> claims = SimpleJson.parseObject(payloadJson);
            Object expObj = claims.get("exp");
            long expSeconds = (expObj instanceof Number n) ? n.longValue() : -1L;
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
            Map<String, Object> claims = SimpleJson.parseObject(payloadJson);
            String userId = SimpleJson.getString(claims, "userId");
            return (userId != null) ? userId : SimpleJson.getString(claims, "sub");
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
            Map<String, Object> claims = SimpleJson.parseObject(payloadJson);
            return SimpleJson.getString(claims, "username");
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
}