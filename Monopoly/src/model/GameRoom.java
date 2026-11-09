package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root quản lý trạng thái và vòng đời của ván chơi.
 *
 * HƯỚNG DẪN TRIỂN KHAI (AGGREGATE ROOT):
 * Quản lý danh sách người chơi, trạng thái phòng (WAITING, PLAYING, FINISHED).
 * Quản lý turnId và xoay vòng lượt chơi (nextTurn).
 * Mọi thao tác đổi lượt chỉ được điều phối từ GameRoom.
 */
public class GameRoom {
    private final String id;
    private final String name;
    private final int maxPlayers;
    private RoomStatus status;
    private final List<Player> players;
    private final Board board;
    private int currentTurnPlayerIndex;
    private int turnId;
    private Dice lastDice;

    public GameRoom(String id, String name, int maxPlayers) {
        this.id = id;
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.status = RoomStatus.WAITING;
        this.players = new ArrayList<>();
        this.board = new Board();
        this.currentTurnPlayerIndex = 0;
        this.turnId = 1;
        this.lastDice = null;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public synchronized RoomStatus getStatus() {
        return this.status;
    }

    public synchronized void setStatus(RoomStatus status) {
        this.status = status;
    }

    public Board getBoard() {
        return this.board;
    }

    public synchronized int getTurnId() {
        return this.turnId;
    }

    public synchronized Dice getLastDice() {
        return this.lastDice;
    }

    public synchronized void setLastDice(Dice lastDice) {
        this.lastDice = lastDice;
    }

    public synchronized boolean addPlayer(Player player) {
        if (player == null) {
            return false;
        }
        if (this.status == RoomStatus.WAITING && this.players.size() < this.maxPlayers) {
            // Tránh thêm trùng id người chơi
            for (Player p : this.players) {
                if (p.getId().equals(player.getId())) {
                    return false;
                }
            }
            return this.players.add(player);
        }
        return false;
    }

    public synchronized boolean removePlayer(String playerId) {
        if (playerId == null || this.players.isEmpty()) {
            return false;
        }
        return this.players.removeIf(p -> p.getId().equals(playerId));
    }

    public synchronized Player getCurrentPlayer() {
        if (this.players.isEmpty()) {
            return null;
        }
        int index = ((this.currentTurnPlayerIndex % this.players.size()) + this.players.size()) % this.players.size();
        return this.players.get(index);
    }

    public synchronized void nextTurn() {
        if (this.players.isEmpty()) {
            return;
        }

        // Tăng turnId
        this.turnId++;

        // Tìm người chơi tiếp theo còn hoạt động (ACTIVE hoặc IN_JAIL, bỏ qua BANKRUPT)
        int initialIndex = this.currentTurnPlayerIndex;
        int size = this.players.size();

        do {
            this.currentTurnPlayerIndex = (this.currentTurnPlayerIndex + 1) % size;
            Player nextPlayer = this.players.get(this.currentTurnPlayerIndex);
            if (nextPlayer.getStatus() != PlayerStatus.BANKRUPT) {
                break;
            }
        } while (this.currentTurnPlayerIndex != initialIndex);
    }

    public synchronized List<Player> getPlayers() {
        return Collections.unmodifiableList(this.players);
    }
}