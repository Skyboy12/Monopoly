package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Ô Đỗ Xe Miễn Phí (Free Parking).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đây là ô an toàn để nghỉ ngơi, không bị phạt tiền và không xảy ra giao dịch.
 */
public class FreeParkingTile extends Tile {

    public FreeParkingTile(int index, String name) {
        super(index, name);
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        // TODO: Xử lý nghỉ ngơi an toàn, không có phí phạt
    }
}
