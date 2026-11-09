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

    public RailroadProperty(int index, String name, int price) {
        super(index, name, price);
    }

    @Override
    public int calculateRent(GameRoom room) {
        if (getOwner() == null) {
            return 0;
        }

        // Gọi hàm getRailroadCount() đã được đóng gói trong Player
        int ownedCount = getOwner().getRailroadCount();
        if (ownedCount <= 0) {
            return 0;
        }

        // Công thức lũy tiến: 25 * 2^(n - 1)
        return 25 * (1 << (ownedCount - 1));
    }
}