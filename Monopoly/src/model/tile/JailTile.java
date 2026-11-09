package model.tile;

import model.GameRoom;
import model.Player;
import model.PlayerStatus;

/**
 * Ô Nhà Tù (Thăm tù hoặc Thụ án).
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Nếu người chơi chỉ dừng chân bình thường: Xem như đang "Thăm tù" (Just Visiting), không phạt.
 * Nếu người chơi đang thụ án tù (status = IN_JAIL): Cập nhật số lượt bị giam giữ hoặc trừ tiền bảo lãnh.
 */
public class JailTile extends Tile {

    public JailTile(int index, String name) {
        super(index, name);
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        if (player == null) {
            return;
        }

        // 1. Nếu người chơi dừng chân bình thường (ACTIVE): Chỉ là ghé thăm tù (Just Visiting), an toàn
        if (player.getStatus() != PlayerStatus.IN_JAIL) {
            return;
        }

        // 2. Nếu đang thụ án tù: tăng số lượt đã ở trong tù
        player.setTurnsInJail(player.getTurnsInJail() + 1);

        // Sau 3 lượt bị giam, bắt buộc nộp phạt $50 bảo lãnh để ra tù
        if (player.getTurnsInJail() >= 3) {
            boolean paid = player.payBail();
            if (!paid) {
                // Không đủ $50 bảo lãnh sau 3 lượt -> Phá sản trước ngân hàng
                player.declareBankruptcy(null);
            }
        }
    }
}