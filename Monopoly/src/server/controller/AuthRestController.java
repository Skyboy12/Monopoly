package server.controller;

import network.GameMessage;
import server.gateway.SimpleJson;
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
    private static final int MIN_USERNAME_LEN = 3;
    private static final int MAX_USERNAME_LEN = 50;
    private static final int MAX_DISPLAY_NAME_LEN = 100;
    private static final int MIN_PASSWORD_LEN = 6;
    private static final int MAX_PASSWORD_LEN = 128;

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
        String cleanUsername = (username == null) ? "" : username.trim();
        if (cleanUsername.length() < MIN_USERNAME_LEN || cleanUsername.length() > MAX_USERNAME_LEN) {
            return error("INVALID_USERNAME", "Username phải có từ 3 đến 50 ký tự.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LEN || password.length() > MAX_PASSWORD_LEN) {
            return error("WEAK_PASSWORD", "Mật khẩu phải có từ 6 đến 128 ký tự.");
        }
        String cleanDisplay = (displayName == null || displayName.isBlank()) ? cleanUsername : displayName.trim();
        if (cleanDisplay.length() > MAX_DISPLAY_NAME_LEN) {
            return error("INVALID_DISPLAY_NAME", "Tên hiển thị không được vượt quá 100 ký tự.");
        }

        if (userRepository.existsByUsername(cleanUsername)) {
            return error("USERNAME_TAKEN", "Username đã tồn tại.");
        }

        String passwordHash = passwordEncoder.encode(password);
        UserAccount saved;
        try {
            saved = userRepository.save(new UserAccount(cleanUsername, passwordHash, cleanDisplay));
        } catch (UserRepository.DuplicateUsernameException e) {
            return error("USERNAME_TAKEN", "Username đã tồn tại.");
        }
        String token = jwtTokenProvider.generateToken(saved.getId(), saved.getUsername());

        String payload = String.format(
                "{\"userId\":\"%s\",\"username\":\"%s\",\"displayName\":\"%s\",\"ratingPoints\":%d,\"accessToken\":\"%s\"}",
                SimpleJson.escape(saved.getId()),
                SimpleJson.escape(saved.getUsername()),
                SimpleJson.escape(saved.getDisplayName()),
                saved.getRatingPoints(),
                SimpleJson.escape(token)
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
        String cleanUsername = username.trim();
        if (cleanUsername.isEmpty() || cleanUsername.length() > MAX_USERNAME_LEN || password.length() > MAX_PASSWORD_LEN) {
            return error("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        Optional<UserAccount> userOpt = userRepository.findByUsername(cleanUsername);
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
            return error("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }

        UserAccount user = userOpt.get();
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String payload = String.format(
                "{\"userId\":\"%s\",\"username\":\"%s\",\"displayName\":\"%s\",\"ratingPoints\":%d,\"accessToken\":\"%s\"}",
                SimpleJson.escape(user.getId()),
                SimpleJson.escape(user.getUsername()),
                SimpleJson.escape(user.getDisplayName()),
                user.getRatingPoints(),
                SimpleJson.escape(token)
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
                SimpleJson.escape(user.getId()),
                SimpleJson.escape(user.getUsername()),
                SimpleJson.escape(user.getDisplayName()),
                user.getRatingPoints()
        );
        return new GameMessage("USER_PROFILE", user.getId(), null, 0, payload);
    }

    private GameMessage error(String code, String message) {
        String payload = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\"}",
                SimpleJson.escape(code),
                SimpleJson.escape(message)
        );
        return new GameMessage("ERROR", null, null, 0, payload);
    }
}