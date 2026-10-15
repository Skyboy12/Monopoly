package model.tile;

import model.GameRoom;

/**
 * Ô đất thông thường trên bàn cờ.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Thuộc nhóm màu (colorGroup), có số nhà (houseCount) và cờ khách sạn (hasHotel).
 * - Cài đặt calculateRent(): tính tiền thuê cơ bản nhân theo số lượng nhà hoặc khách sạn.
 */
public class StreetProperty extends PropertyTile {
    private final String colorGroup;
    private final int baseRent;
    private int houseCount;
    private boolean hasHotel;

    public StreetProperty(int index, String name, int price, String colorGroup, int baseRent) {
        super(index, name, price);
        this.colorGroup = colorGroup;
        this.baseRent = baseRent;
        this.houseCount = 0;
        this.hasHotel = false;
    }

    public String getColorGroup() {
        // TODO: Trả về nhóm màu của ô đất
        return null;
    }

    public int getHouseCount() {
        // TODO: Trả về số lượng nhà hiện tại
        return 0;
    }

    public void setHouseCount(int houseCount) {
        // TODO: Cập nhật số lượng nhà
    }

    public boolean isHasHotel() {
        // TODO: Trả về true nếu đã xây khách sạn
        return false;
    }

    public void setHasHotel(boolean hasHotel) {
        // TODO: Cập nhật trạng thái khách sạn
    }

    public int getBaseRent() {
        // TODO: Trả về tiền thuê cơ bản
        return 0;
    }

    @Override
    public int calculateRent(GameRoom room) {
        // TODO: Tính tiền thuê theo số lượng nhà hoặc khách sạn
        return 0;
    }
}
