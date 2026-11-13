package server.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Thực thể lưu trữ thông tin tài khoản người chơi trong Database.
 * Chỉ lưu dữ liệu định danh và điểm xếp hạng (Rating Points), không lưu state ván đang chơi.
 */
public class UserAccount {
    private final String id;
    private final String username;
    private String passwordHash;
    private String displayName;
    private int ratingPoints;
    private final Instant createdAt;

    public UserAccount(String username, String passwordHash, String displayName) {
        this(UUID.randomUUID().toString(), username, passwordHash, displayName, 1000, Instant.now());
    }

    public UserAccount(String id, String username, String passwordHash,
                       String displayName, int ratingPoints, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.ratingPoints = ratingPoints;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getRatingPoints() {
        return ratingPoints;
    }

    public void setRatingPoints(int ratingPoints) {
        this.ratingPoints = Math.max(0, ratingPoints);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}