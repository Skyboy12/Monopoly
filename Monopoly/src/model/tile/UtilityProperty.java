package model.tile;

import model.GameRoom;

/**
 * Ô công ty tiện ích (Điện, Nước).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Cài đặt calculateRent(): lấy tổng điểm xúc xắc vừa gieo từ room.getLastDice().
 * - Nếu sở hữu 1 tiện ích: tiền thuê = diceTotal * 4.
 * - Nếu sở hữu cả 2 tiện ích: tiền thuê = diceTotal * 10.
 */
public class UtilityProperty extends PropertyTile {

    public UtilityProperty(int index, String name, int price) {
        super(index, name, price);
    }

    @Override
    public int calculateRent(GameRoom room) {
        // TODO: Tính tiền thuê dựa trên số xúc xắc vừa gieo và số tiện ích người đó sở hữu
        return 0;
    }
}
