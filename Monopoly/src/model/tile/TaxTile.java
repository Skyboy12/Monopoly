package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Ô đóng thuế (Income Tax, Luxury Tax).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Khi người chơi đáp vào ô, khấu trừ taxAmount từ tài khoản người chơi.
 */
public class TaxTile extends Tile {
    private final int taxAmount;

    public TaxTile(int index, String name, int taxAmount) {
        super(index, name);
        this.taxAmount = taxAmount;
    }

    public int getTaxAmount() {
        // TODO: Trả về số tiền thuế phải nộp
        return 0;
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        // TODO: Khấu trừ số tiền thuế (taxAmount) từ ví của người chơi
    }
}
