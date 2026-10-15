package model.tile;

import model.GameRoom;

/**
 * Ô nhà ga / bến xe.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Cài đặt calculateRent(): tính tiền thuê nhân đôi theo số lượng nhà ga mà chủ đất đang sở hữu
 *   (Ví dụ: 1 ga: $25, 2 ga: $50, 3 ga: $100, 4 ga: $200).
 */
public class RailroadProperty extends PropertyTile {

    public RailroadProperty(int index, String name, int price) {
        super(index, name, price);
    }

    @Override
    public int calculateRent(GameRoom room) {
        // TODO: Tính tiền thuê lũy tiến dựa theo số lượng ga người sở hữu đang nắm giữ
        return 0;
    }
}
