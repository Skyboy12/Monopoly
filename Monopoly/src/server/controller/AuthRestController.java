package server.controller;

import network.GameMessage;
import server.persistence.UserAccount;
import server.persistence.UserRepository;
import server.security.JwtTokenProvider;
import server.security.PasswordEncoderUtil;

import java.util.Optional;

/**
 * Controller xử lý REST API Đăng ký, Đăng nhập và Truy vấn thông tin cá nhân.
 * Base URL: /api/auth
 */
public class AuthRestController {
    private final UserRepository userRepository;
    private final PasswordEncoderUtil passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthRestController(UserRepository userRepository,
                              PasswordEncoderUtil passwordEncoder,
                              JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * POST /api/auth/register
     */
    public GameMessage register(String username, String password, String displayName) {
        if (username == null || username.trim().length() < 3) {
            return error("INVALID_USERNAME", "Username phải có ít nhất 3 ký tự.");
        }
        if (password == null || password.length() < 6) {
            return error("WEAK_PASSWORD", "Mật khẩu phải có ít nhất 6 ký tự.");
        }
        String cleanUsername = username.trim();
        if (userRepository.existsByUsername(cleanUsername)) {
            return error("USERNAME_TAKEN", "Username đã tồn tại.");
        }

        String cleanDisplay = (displayName == null || displayName.isBlank()) ? cleanUsername : displayName.trim();
        String passwordHash = passwordEncoder.encode(password);

        UserAccount saved = userRepository.save(new UserAccount(cleanUsername, passwordHash, cleanDisplay));
        String token = jwtTokenProvider.generateToken(saved.getId(), saved.getUsername());

        String payload = String.format(
                "{\"userId\":\"%s\",\"username\":\"%s\",\"displayName\":\"%s\",\"ratingPoints\":%d,\"accessToken\":\"%s\"}",
                escape(saved.getId()), escape(saved.getUsername()), escape(saved.getDisplayName()),
                saved.getRatingPoints(), escape(token)
        );
        return new GameMessage("AUTH_REGISTERED", saved.getId(), null, 0, payload);
    }

    /**
     * POST /api/auth/login
     */
    public GameMessage login(String username, String password) {
        if (username == null || password == null) {
            return error("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        Optional<UserAccount> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
            return error("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }

        UserAccount user = userOpt.get();
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String payload = String.format(
                "{\"userId\":\"%s\",\"username\":\"%s\",\"displayName\":\"%s\",\"ratingPoints\":%d,\"accessToken\":\"%s\"}",
                escape(user.getId()), escape(user.getUsername()), escape(user.getDisplayName()),
                user.getRatingPoints(), escape(token)
        );
        return new GameMessage("AUTH_LOGGED_IN", user.getId(), null, 0, payload);
    }

    /**
     * GET /api/auth/me (Yêu cầu JWT Bearer Token)
     */
    public GameMessage getCurrentUserProfile(String authorizationHeader) {
        if (!jwtTokenProvider.validateToken(authorizationHeader)) {
            return error("UNAUTHORIZED", "Token không hợp lệ hoặc đã hết hạn.");
        }
        String userId = jwtTokenProvider.extractUserId(authorizationHeader);
        Optional<UserAccount> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return error("USER_NOT_FOUND", "Không tìm thấy tài khoản.");
        }

        UserAccount user = userOpt.get();
        String payload = String.format(
                "{\"userId\":\"%s\",\"username\":\"%s\",\"displayName\":\"%s\",\"ratingPoints\":%d}",
                escape(user.getId()), escape(user.getUsername()), escape(user.getDisplayName()),
                user.getRatingPoints()
        );
        return new GameMessage("USER_PROFILE", user.getId(), null, 0, payload);
    }

    private GameMessage error(String code, String message) {
        String payload = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", escape(code), escape(message));
        return new GameMessage("ERROR", null, null, 0, payload);
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}