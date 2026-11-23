package model.tile;

import model.GameRoom;
import model.Player;

/**
 * Lớp trừu tượng cho các ô có thể sở hữu/mua bán/cầm cố (Street, Railroad, Utility).
 * Thể hiện tính Inheritance và Encapsulation.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Lưu giữ giá tiền, chủ sở hữu (owner) và trạng thái thế chấp (mortgaged).
 * Cài đặt onLand(): nếu ô đã có chủ khác và không thế chấp -> trừ tiền người chơi trả cho chủ đất.
 * calculateRent(): phương thức trừu tượng để từng loại con tự tính tiền thuê.
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
        return this.price;
    }

    public Player getOwner() {
        return this.owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public boolean isMortgaged() {
        return this.mortgaged;
    }

    public void setMortgaged(boolean mortgaged) {
        this.mortgaged = mortgaged;
    }

    public boolean hasOwner() {
        return this.owner != null;
    }

    /**
     * Phương thức đa hình tính tiền thuê dựa theo từng loại tài sản cụ thể.
     */
    public abstract int calculateRent(GameRoom room);

    @Override
    public void onLand(Player player, GameRoom room) {
        if (player == null) {
            return;
        }

        // 1. Nếu chưa có chủ -> Game Engine sẽ xử lý mở phase quyết định mua hay không
        if (!hasOwner()) {
            return;
        }

        // 2. Nếu chủ sở hữu chính là người chơi hiện tại, đất đang thế chấp, hoặc ô đang đóng băng -> bỏ qua
        if (this.owner == player || this.mortgaged || isFrozen()) {
            return;
        }

        // 2b. Nếu người chơi có Khiên Hộ mệnh (hoặc đang sở hữu trong túi đồ ở pha ON_LAND) -> kích hoạt & miễn tiền thuê
        if (!player.isRentShieldActive() && player.getInventory() != null) {
            model.item.Item shield = player.getInventory().getItemById("RENT_SHIELD");
            if (shield != null && shield.canUse(player, room, null)) {
                if (shield.applyEffect(player, room, null)) {
                    player.getInventory().removeItem("RENT_SHIELD");
                }
            }
        }
        if (player.isRentShieldActive()) {
            player.setRentShieldActive(false);
            return;
        }

        // 3. Nếu đã có chủ khác -> tính tiền thuê đa hình (nhân hệ số sự kiện nếu có) và yêu cầu player trả tiền cho chủ đất
        int multiplier = (room != null) ? room.getRentMultiplier() : 1;
        int rent = calculateRent(room) * multiplier;
        if (rent > 0) {
            boolean success = player.pay(rent, this.owner);
            if (!success) {
                // Người chơi không đủ tiền trả nợ -> Tuyên bố phá sản trước chủ nợ (owner)
                player.declareBankruptcy(this.owner);
            }
        }
    }
}