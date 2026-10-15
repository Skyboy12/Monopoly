package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Ô xuất phát (GO). Nhận thưởng khi đáp chân hoặc đi ngang qua.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Khi người chơi đáp vào hoặc đi qua ô GO, cộng tiền thưởng GO_REWARD ($200) cho người chơi.
 */
public class GoTile extends Tile {
    public static final int GO_REWARD = 200;

    public GoTile(int index, String name) {
        super(index, name);
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        // TODO: Cộng tiền thưởng GO_REWARD cho người chơi khi đáp chân vào ô GO
    }
}
