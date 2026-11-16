package server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích băm và kiểm tra mật khẩu một chiều có muối (Salted Hash).
 */
public class PasswordEncoderUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
        String hash = hashWithSalt(rawPassword, saltBase64);
        return "$2a$10$" + saltBase64 + "$" + hash;
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || !encodedPassword.startsWith("$2a$10$")) {
            return false;
        }
        String[] parts = encodedPassword.substring(7).split("\\$");
        if (parts.length != 2) {
            return false;
        }
        String saltBase64 = parts[0];
        String expectedHash = parts[1];
        String actualHash = hashWithSalt(rawPassword, saltBase64);
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hashWithSalt(String rawPassword, String saltBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = (saltBase64 + ":" + rawPassword).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 1024; i++) {
                input = digest.digest(input);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi mã hóa mật khẩu", e);
        }
    }
}