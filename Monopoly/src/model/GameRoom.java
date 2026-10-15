package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate Root quản lý trạng thái và vòng đời của ván chơi.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI (AGGREGATE ROOT):
 * - Quản lý danh sách người chơi, trạng thái phòng (WAITING, PLAYING, FINISHED).
 * - Quản lý turnId và xoay vòng lượt chơi (nextTurn).
 * - Mọi thao tác đổi lượt chỉ được điều phối từ GameRoom.
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
        // TODO: Trả về trạng thái phòng chơi
        return null;
    }

    public synchronized void setStatus(RoomStatus status) {
        // TODO: Cập nhật trạng thái phòng chơi
    }

    public Board getBoard() {
        // TODO: Trả về bàn cờ của phòng
        return null;
    }

    public synchronized int getTurnId() {
        // TODO: Trả về số thứ tự lượt hiện tại (turnId)
        return 0;
    }

    public synchronized Dice getLastDice() {
        // TODO: Trả về kết quả xúc xắc vừa gieo trong phòng
        return null;
    }

    public synchronized void setLastDice(Dice lastDice) {
        // TODO: Lưu lại kết quả xúc xắc vừa gieo
    }

    public synchronized boolean addPlayer(Player player) {
        // TODO: Thêm người chơi mới nếu phòng đang WAITING và chưa đạt maxPlayers
        return false;
    }

    public synchronized boolean removePlayer(String playerId) {
        // TODO: Xóa người chơi khỏi phòng theo ID
        return false;
    }

    public synchronized Player getCurrentPlayer() {
        // TODO: Trả về người chơi đang có lượt hành động
        return null;
    }

    public synchronized void nextTurn() {
        // TODO: Chuyển lượt sang người chơi tiếp theo và tăng turnId
    }

    public synchronized List<Player> getPlayers() {
        // TODO: Trả về danh sách người chơi trong phòng
        return null;
    }
}
