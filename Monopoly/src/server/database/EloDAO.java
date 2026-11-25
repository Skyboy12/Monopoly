package server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EloDAO {
    private static final int K_FACTOR = 32;
    private static final int MIN_ELO = 100;

    public static double expectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    public void recordMatch(int winnerId, int loserId) throws SQLException {
        if (winnerId == loserId) {
            throw new IllegalArgumentException("Winner and loser must be different users");
        }
        recordMultiplayerMatch(winnerId, List.of(loserId));
    }

    public void recordMultiplayerMatch(int winnerId, List<Integer> loserIds) throws SQLException {
        if (loserIds == null || loserIds.isEmpty()) {
            throw new IllegalArgumentException("At least one loser is required");
        }
        Set<Integer> uniqueLosers = new LinkedHashSet<>();
        for (Integer loserId : loserIds) {
            if (loserId == null || loserId == winnerId) {
                throw new IllegalArgumentException("Winner and losers must be valid and distinct users");
            }
            uniqueLosers.add(loserId);
        }

        List<Integer> sortedIds = new ArrayList<>(1 + uniqueLosers.size());
        sortedIds.add(winnerId);
        sortedIds.addAll(uniqueLosers);
        Collections.sort(sortedIds);

        try (Connection conn = Connector.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<Integer, Integer> eloById = getElosForUpdate(conn, sortedIds);
                int winnerElo = eloById.get(winnerId);
                int currentWinnerElo = winnerElo;

                for (int loserId : uniqueLosers) {
                    int loserElo = eloById.get(loserId);
                    double expectedWin = expectedScore(winnerElo, loserElo);
                    double expectedLose = expectedScore(loserElo, winnerElo);

                    int winnerDelta = (int) Math.round(K_FACTOR * (1.0 - expectedWin));
                    int nextWinnerElo = Math.max(MIN_ELO, currentWinnerElo + winnerDelta);
                    int newLoserElo = Math.max(MIN_ELO, (int) Math.round(loserElo + K_FACTOR * (0.0 - expectedLose)));

                    updatePlayerStats(conn, loserId, newLoserElo, false);
                    insertMatchHistory(conn, winnerId, loserId, currentWinnerElo, nextWinnerElo, loserElo, newLoserElo);
                    currentWinnerElo = nextWinnerElo;
                }

                updatePlayerStats(conn, winnerId, currentWinnerElo, true);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<User> getLeaderboard(int limit) throws SQLException {
        String sql = "SELECT id, username, elo, wins, losses, games_played, created_at " +
                     "FROM users ORDER BY elo DESC, wins DESC LIMIT ?";
        List<User> leaderboard = new ArrayList<>();
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    leaderboard.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getInt("elo"),
                        rs.getInt("wins"),
                        rs.getInt("losses"),
                        rs.getInt("games_played"),
                        rs.getTimestamp("created_at")
                    ));
                }
            }
        }
        return leaderboard;
    }

    private static Map<Integer, Integer> getElosForUpdate(Connection conn, List<Integer> sortedIds) throws SQLException {
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < sortedIds.size(); i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
        }
        String sql = "SELECT id, elo FROM users WHERE id IN (" + placeholders + ") ORDER BY id ASC FOR UPDATE";
        Map<Integer, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < sortedIds.size(); i++) {
                ps.setInt(i + 1, sortedIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("id"), rs.getInt("elo"));
                }
            }
        }
        for (int id : sortedIds) {
            if (!result.containsKey(id)) {
                throw new SQLException("User not found: " + id);
            }
        }
        return result;
    }

    private static void updatePlayerStats(Connection conn, int userId, int newElo, boolean won) throws SQLException {
        String sql = won
            ? "UPDATE users SET elo = ?, wins = wins + 1, games_played = games_played + 1 WHERE id = ?"
            : "UPDATE users SET elo = ?, losses = losses + 1, games_played = games_played + 1 WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newElo);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private static void insertMatchHistory(
        Connection conn,
        int winnerId,
        int loserId,
        int winnerEloBefore,
        int winnerEloAfter,
        int loserEloBefore,
        int loserEloAfter
    ) throws SQLException {
        String sql = "INSERT INTO match_history (winner_id, loser_id, winner_elo_before, winner_elo_after, loser_elo_before, loser_elo_after) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, winnerId);
            ps.setInt(2, loserId);
            ps.setInt(3, winnerEloBefore);
            ps.setInt(4, winnerEloAfter);
            ps.setInt(5, loserEloBefore);
            ps.setInt(6, loserEloAfter);
            ps.executeUpdate();
        }
    }
}
