package server.persistence;

import server.database.Connector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository truy xuất dữ liệu tài khoản người dùng (hỗ trợ cả in-memory và MySQL/MariaDB).
 */
public class UserRepository {
    public static final class DuplicateUsernameException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DuplicateUsernameException(String message) {
            super(message);
        }
    }

    private final boolean useDatabase;
    private final ConcurrentHashMap<String, UserAccount> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserAccount> usersByUsername = new ConcurrentHashMap<>();
    private final Object inMemoryLock = new Object();

    public UserRepository() {
        this(false);
    }

    public UserRepository(boolean useDatabase) {
        this.useDatabase = useDatabase;
    }

    public static UserRepository withMySql() {
        return new UserRepository(true);
    }

    public boolean isDatabaseEnabled() {
        return useDatabase;
    }

    public UserAccount save(UserAccount user) {
        if (user == null) {
            throw new IllegalArgumentException("UserAccount không được null.");
        }
        if (!useDatabase) {
            synchronized (inMemoryLock) {
                String lowerUsername = user.getUsername().toLowerCase();
                UserAccount existingByUsername = usersByUsername.get(lowerUsername);
                if (existingByUsername != null && !existingByUsername.getId().equals(user.getId())) {
                    throw new DuplicateUsernameException("Username đã tồn tại.");
                }
                usersById.put(user.getId(), user);
                usersByUsername.put(lowerUsername, user);
                return user;
            }
        }
        return saveToDatabase(user);
    }

    public Optional<UserAccount> findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        if (!useDatabase) {
            return Optional.ofNullable(usersById.get(userId));
        }

        int numericId;
        try {
            numericId = Integer.parseInt(userId.trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        String sql = "SELECT id, username, display_name, password_hash, elo, created_at FROM users WHERE id = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, numericId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi truy vấn người dùng theo ID từ cơ sở dữ liệu.", e);
        }
    }

    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        if (!useDatabase) {
            return Optional.ofNullable(usersByUsername.get(username.toLowerCase()));
        }

        String sql = "SELECT id, username, display_name, password_hash, elo, created_at "
                + "FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi truy vấn người dùng theo username từ cơ sở dữ liệu.", e);
        }
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (!useDatabase) {
            return usersByUsername.containsKey(username.toLowerCase());
        }

        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi kiểm tra tồn tại username trong cơ sở dữ liệu.", e);
        }
    }

    private UserAccount saveToDatabase(UserAccount user) {
        Integer existingId = tryParseInt(user.getId());
        if (existingId != null) {
            String updateSql = "UPDATE users SET display_name = ?, password_hash = ?, elo = ? WHERE id = ?";
            try (Connection conn = Connector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, user.getDisplayName());
                ps.setString(2, user.getPasswordHash());
                ps.setInt(3, user.getRatingPoints());
                ps.setInt(4, existingId);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    return user;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Lỗi cập nhật tài khoản trong cơ sở dữ liệu.", e);
            }
        }

        String insertSql = "INSERT INTO users (username, display_name, password_hash, elo) VALUES (?, ?, ?, ?)";
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getPasswordHash());
            ps.setInt(4, user.getRatingPoints());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    String generatedId = String.valueOf(keys.getInt(1));
                    return new UserAccount(
                            generatedId,
                            user.getUsername(),
                            user.getPasswordHash(),
                            user.getDisplayName(),
                            user.getRatingPoints(),
                            user.getCreatedAt()
                    );
                }
            }
            throw new IllegalStateException("Không lấy được ID người dùng vừa tạo từ cơ sở dữ liệu.");
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                throw new DuplicateUsernameException("Username đã tồn tại.");
            }
            throw new IllegalStateException("Lỗi lưu tài khoản mới vào cơ sở dữ liệu.", e);
        }
    }

    private static UserAccount mapRow(ResultSet rs) throws SQLException {
        String id = String.valueOf(rs.getInt("id"));
        String username = rs.getString("username");
        String displayName = rs.getString("display_name");
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }
        String passwordHash = rs.getString("password_hash");
        int elo = rs.getInt("elo");
        Timestamp ts = rs.getTimestamp("created_at");
        Instant createdAt = (ts != null) ? ts.toInstant() : Instant.now();
        return new UserAccount(id, username, passwordHash, displayName, elo, createdAt);
    }

    private static Integer tryParseInt(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
