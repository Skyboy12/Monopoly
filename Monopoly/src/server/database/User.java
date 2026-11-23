package server.database;

import java.sql.Timestamp;

public class User {
    private final int id;
    private final String username;
    private int elo;
    private int wins;
    private int losses;
    private int gamesPlayed;
    private final Timestamp createdAt;

    public User(int id, String username, int elo, int wins, int losses, int gamesPlayed, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
        this.gamesPlayed = gamesPlayed;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public int getElo() { return elo; }
    public void setElo(int elo) { this.elo = elo; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }
    public Timestamp getCreatedAt() { return createdAt; }
}
