package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Ô đóng thuế (Income Tax, Luxury Tax).
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Khi người chơi đáp vào ô, khấu trừ taxAmount từ tài khoản người chơi.
 */
public class TaxTile extends Tile {
    private final int taxAmount;

    public TaxTile(int index, String name, int taxAmount) {
        super(index, name);
        this.taxAmount = taxAmount;
    }

    public int getTaxAmount() {
        return this.taxAmount;
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        if (player == null) {
            return;
        }

        // Khấu trừ số tiền thuế (taxAmount) từ tài khoản người chơi nộp vào ngân hàng
        boolean success = player.deductMoney(this.taxAmount);
        if (!success) {
            // Không đủ tiền đóng thuế -> Tuyên bố phá sản trước ngân hàng (creditor = null)
            player.declareBankruptcy(null);
        }
    }
}