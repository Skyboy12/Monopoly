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

        int utilityCount = getOwner().getUtilityCount();
        if (utilityCount <= 0) {
            return 0;
        }

        // Gọi getTotal() từ đối tượng Dice, tránh so sánh trực tiếp Dice với int
        int diceTotal = (room != null && room.getLastDice() != null && room.getLastDice().getTotal() > 0)
                ? room.getLastDice().getTotal()
                : 7;

        int multiplier = (utilityCount >= 2) ? 10 : 4;

        return diceTotal * multiplier;
    }
}