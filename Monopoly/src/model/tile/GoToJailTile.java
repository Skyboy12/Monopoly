package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Ô Vào Tù (Go To Jail).
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Khi dừng tại đây, di chuyển người chơi thẳng về ô Nhà Tù (index = 10) mà không được nhận thưởng GO.
 * Chuyển trạng thái người chơi thành PlayerStatus.IN_JAIL.
 */
public class GoToJailTile extends Tile {
    public static final int JAIL_INDEX = 10;

    public GoToJailTile(int index, String name) {
        super(index, name);
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        if (player == null || isFrozen()) {
            return;
        }
        // Đưa thẳng người chơi về JAIL_INDEX (10), đặt IN_JAIL và reset turnsInJail = 0
        player.sendToJail(JAIL_INDEX);
    }
}