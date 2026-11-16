package model.event;

import model.GameRoom;

/**
 * Sự kiện Toàn cục - Cơn Điên Bất Động Sản:
 * Nhân 3 lần tiền thuê toàn bộ ô đất trong 3 lượt tiếp theo.
 */
public class PropertyBoomEvent extends GameEvent {

    public PropertyBoomEvent() {
        super(
                "PROPERTY_BOOM",
                "Cơn Điên Bất Động Sản",
                "Nhân 3 lần tiền thuê toàn bộ ô đất trong 3 lượt tiếp theo.",
                3
        );
    }

    @Override
    public void onTrigger(GameRoom room) {
        if (room != null) {
            room.setRentMultiplier(3);
        }
    }

    @Override
    public void onExpire(GameRoom room) {
        if (room != null) {
            room.setRentMultiplier(1);
        }
    }
}
