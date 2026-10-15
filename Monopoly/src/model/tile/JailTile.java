package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Ô Nhà Tù (Thăm tù hoặc Thụ án).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Nếu người chơi chỉ dừng chân bình thường: Xem như đang "Thăm tù" (Just Visiting), không phạt.
 * - Nếu người chơi đang thụ án tù (status = IN_JAIL): Cập nhật số lượt bị giam giữ hoặc trừ tiền bảo lãnh.
 */
public class JailTile extends Tile {

    public JailTile(int index, String name) {
        super(index, name);
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        // TODO: Xử lý hành vi thăm tù an toàn hoặc cập nhật trạng thái khi đang ở tù
    }
}
