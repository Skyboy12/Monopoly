package model.tile;

import model.GameRoom;

/**
 * Ô công ty tiện ích (Điện, Nước).
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Cài đặt calculateRent(): lấy tổng điểm xúc xắc vừa gieo từ room.getLastDice().
 * Nếu sở hữu 1 tiện ích: tiền thuê = diceTotal * 4.
 * Nếu sở hữu cả 2 tiện ích: tiền thuê = diceTotal * 10.
 */
public class UtilityProperty extends PropertyTile {

    public UtilityProperty(int index, String name, int price) {
        super(index, name, price);
    }

    @Override
    public int calculateRent(GameRoom room) {
        if (getOwner() == null) {
            return 0;
        }

        // Lấy số tiện ích chủ sở hữu đang nắm giữ (1 hoặc 2)
        int utilityCount = getOwner().getUtilityCount();
        if (utilityCount <= 0) {
            return 0;
        }

        // Lấy tổng điểm xúc xắc vừa gieo từ phòng chơi (mặc định tối thiểu là 2 nếu chưa có dữ liệu)
        int diceTotal = (room != null && room.getLastDice() > 0) ? room.getLastDice() : 7;

        // Nếu sở hữu cả 2 tiện ích: nhân 10, nếu sở hữu 1 tiện ích: nhân 4
        int multiplier = (utilityCount >= 2) ? 10 : 4;

        return diceTotal * multiplier;
    }
}