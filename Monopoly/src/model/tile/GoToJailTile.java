package model.tile;

import model.GameRoom;
import model.Player;
import model.PlayerStatus;

/**
 * Ô Vào Tù (Go To Jail).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Khi dừng tại đây, di chuyển người chơi thẳng về ô Nhà Tù (index = 10) mà không được nhận thưởng GO.
 * - Chuyển trạng thái người chơi thành PlayerStatus.IN_JAIL.
 */
public class GoToJailTile extends Tile {
    public static final int JAIL_INDEX = 10;

    public GoToJailTile(int index, String name) {
        super(index, name);
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        // TODO:
        // 1. Cập nhật vị trí người chơi về JAIL_INDEX (10)
        // 2. Đổi trạng thái người chơi sang PlayerStatus.IN_JAIL
    }
}
