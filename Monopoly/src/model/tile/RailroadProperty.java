package model.tile;

import model.GameRoom;

/**
 * Ô nhà ga / bến xe.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Cài đặt calculateRent(): tính tiền thuê nhân đôi theo số lượng nhà ga mà chủ đất đang sở hữu
 * (Ví dụ: 1 ga: $25, 2 ga: $50, 3 ga: $100, 4 ga: $200).
 */
public class RailroadProperty extends PropertyTile {
    public static final int BASE_RENT = 25_000;

    public RailroadProperty(int index, String name, int price) {
        super(index, name, price);
    }

    public static int rentForCount(int ownedCount) {
        if (ownedCount <= 0) {
            return 0;
        }
        int clamped = Math.min(ownedCount, 4);
        return BASE_RENT * (1 << (clamped - 1));
    }

    @Override
    public int calculateRent(GameRoom room) {
        if (getOwner() == null) {
            return 0;
        }

        // Gọi hàm getRailroadCount() đã được đóng gói trong Player
        int ownedCount = getOwner().getRailroadCount();
        return rentForCount(ownedCount);
    }
}