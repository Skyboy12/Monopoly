package server.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích băm và kiểm tra mật khẩu một chiều có muối (PBKDF2WithHmacSHA256, tương thích ngược $2a$10$).
 */
public class PasswordEncoderUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int PBKDF2_KEY_BITS = 256;

    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
        String hash = hashPbkdf2(rawPassword, salt, PBKDF2_ITERATIONS);
        return "$pbkdf2$" + PBKDF2_ITERATIONS + "$" + saltBase64 + "$" + hash;
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("$pbkdf2$")) {
            String[] parts = encodedPassword.split("\\$");
            if (parts.length != 5) {
                return false;
            }
            int iterations;
            try {
                iterations = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (iterations <= 0) {
                return false;
            }
            byte[] salt;
            try {
                salt = Base64.getUrlDecoder().decode(parts[3]);
            } catch (IllegalArgumentException e) {
                return false;
            }
            String expectedHash = parts[4];
            String actualHash = hashPbkdf2(rawPassword, salt, iterations);
            return MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    actualHash.getBytes(StandardCharsets.UTF_8)
            );
        }
        if (encodedPassword.startsWith("$2a$10$")) {
            String[] parts = encodedPassword.substring(7).split("\\$");
            if (parts.length != 2) {
                return false;
            }
            String saltBase64 = parts[0];
            String expectedHash = parts[1];
            String actualHash = hashLegacyWithSalt(rawPassword, saltBase64);
            return MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    actualHash.getBytes(StandardCharsets.UTF_8)
            );
        }
        return false;
    }

    private String hashPbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi mã hóa mật khẩu PBKDF2", e);
        }
    }

    private String hashLegacyWithSalt(String rawPassword, String saltBase64) {
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