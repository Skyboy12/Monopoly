package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Lớp trừu tượng cơ sở đại diện cho 1 ô trên bàn cờ.
 * Thể hiện tính Abstraction và Polymorphism qua method onLand.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Khi người chơi đáp vào ô, GameEngine chỉ cần gọi: tile.onLand(player, room)
 * - Tuyệt đối không dùng if-else / switch-case để kiểm tra loại ô cờ.
 */
public abstract class Tile {
    private final int index;
    private final String name;

    public Tile(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    /**
     * Hành vi khi một người chơi đáp chân vào ô cờ.
     * Mỗi lớp con của Tile sẽ override phương thức này để tự định nghĩa nghiệp vụ.
     *
     * @param player Người chơi dừng chân tại ô
     * @param room   Phòng chơi hiện tại
     */
    public abstract void onLand(Player player, GameRoom room);
}
