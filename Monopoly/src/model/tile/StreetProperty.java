package model.tile;

import model.GameRoom;

/**
 * Ô đất thông thường trên bàn cờ.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Thuộc nhóm màu (colorGroup), có số nhà (houseCount) và cờ khách sạn (hasHotel).
 * Cài đặt calculateRent(): tính tiền thuê cơ bản nhân theo số lượng nhà hoặc khách sạn.
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
        return this.colorGroup;
    }

    public int getHouseCount() {
        return this.houseCount;
    }

    public void setHouseCount(int houseCount) {
        this.houseCount = Math.max(0, houseCount);
    }

    public boolean isHasHotel() {
        return this.hasHotel;
    }

    public void setHasHotel(boolean hasHotel) {
        this.hasHotel = hasHotel;
    }

    public int getBaseRent() {
        return this.baseRent;
    }

    @Override
    public int calculateRent(GameRoom room) {
        if (getOwner() == null) {
            return 0;
        }

        // Nếu có khách sạn: tiền thuê gấp 10 lần tiền cơ bản
        if (this.hasHotel) {
            return this.baseRent * 10;
        }

        // Nếu có nhà (1-4 nhà): tiền thuê tăng dần theo số nhà
        if (this.houseCount > 0) {
            return this.baseRent * (1 + this.houseCount * 2);
        }

        // Đất trống chưa xây nhà
        return this.baseRent;
    }
}