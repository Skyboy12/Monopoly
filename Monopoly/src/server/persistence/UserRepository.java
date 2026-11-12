package server.persistence;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository truy xuất dữ liệu tài khoản người dùng.
 */
public class UserRepository {
    private final ConcurrentHashMap<String, UserAccount> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserAccount> usersByUsername = new ConcurrentHashMap<>();

    public UserAccount save(UserAccount user) {
        if (user == null) {
            throw new IllegalArgumentException("UserAccount không được null.");
        }
        usersById.put(user.getId(), user);
        usersByUsername.put(user.getUsername().toLowerCase(), user);
        return user;
    }

    public Optional<UserAccount> findById(String userId) {
        if (userId == null) return Optional.empty();
        return Optional.ofNullable(usersById.get(userId));
    }

    public Optional<UserAccount> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return Optional.ofNullable(usersByUsername.get(username.toLowerCase()));
    }

    public boolean existsByUsername(String username) {
        if (username == null) return false;
        return usersByUsername.containsKey(username.toLowerCase());
    }
}