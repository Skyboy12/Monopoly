package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Lớp trừu tượng cho các ô có thể sở hữu/mua bán/cầm cố (Street, Railroad, Utility).
 * Thể hiện tính Inheritance và Encapsulation.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Lưu giữ giá tiền, chủ sở hữu (owner) và trạng thái thế chấp (mortgaged).
 * - Cài đặt onLand(): nếu ô đã có chủ khác và không thế chấp -> trừ tiền người chơi trả cho chủ đất.
 * - calculateRent(): phương thức trừu tượng để từng loại con tự tính tiền thuê.
 */
public abstract class PropertyTile extends Tile {
    private final int price;
    private Player owner;
    private boolean mortgaged;

    public PropertyTile(int index, String name, int price) {
        super(index, name);
        this.price = price;
        this.owner = null;
        this.mortgaged = false;
    }

    public int getPrice() {
        // TODO: Trả về giá mua ô đất
        return 0;
    }

    public Player getOwner() {
        // TODO: Trả về người chơi đang sở hữu ô đất này
        return null;
    }

    public void setOwner(Player owner) {
        // TODO: Cập nhật chủ sở hữu mới
    }

    public boolean isMortgaged() {
        // TODO: Trả về true nếu ô đất đang bị thế chấp
        return false;
    }

    public void setMortgaged(boolean mortgaged) {
        // TODO: Cập nhật trạng thái thế chấp
    }

    public boolean hasOwner() {
        // TODO: Kiểm tra xem ô đã có chủ hay chưa (owner != null)
        return false;
    }

    /**
     * Phương thức đa hình tính tiền thuê dựa theo từng loại tài sản cụ thể.
     */
    public abstract int calculateRent(GameRoom room);

    @Override
    public void onLand(Player player, GameRoom room) {
        // TODO:
        // 1. Nếu chưa có chủ -> chờ người chơi đưa ra quyết định mua hay không
        // 2. Nếu chủ sở hữu chính là người chơi hiện tại hoặc đất đang thế chấp -> bỏ qua
        // 3. Nếu đã có chủ khác -> tính tiền thuê bằng calculateRent(room) và yêu cầu player trả tiền cho chủ đất
    }
}
