package model;

import model.item.Inventory;
import model.tile.PropertyTile;

import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể người chơi trong ván cờ.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI (ĐÓNG GÓI - ENCAPSULATION):
 * - Tất cả các trường dữ liệu (tiền, vị trí, tài sản) phải là private.
 * - Tuyệt đối không cho phép bên ngoài gán trực tiếp: player.money = ...
 * - Mọi biến động tài sản/trạng thái phải thông qua các method nghiệp vụ xác thực.
 */
public class Player {
    private final String id;
    private final String name;
    private int money;
    private int position;
    private PlayerStatus status;
    private final List<PropertyTile> ownedProperties;
    private final Inventory inventory;
    private boolean isBot;
    private int turnsInJail;

    public Player(String id, String name, int initialMoney) {
        this.id = id;
        this.name = name;
        this.money = initialMoney;
        this.position = 0;
        this.status = PlayerStatus.ACTIVE;
        this.ownedProperties = new ArrayList<>();
        this.inventory = new Inventory(5);
        this.isBot = false;
        this.turnsInJail = 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public synchronized int getMoney() {
        // TODO: Trả về số dư tiền hiện tại của người chơi
        return 0;
    }

    public synchronized int getPosition() {
        // TODO: Trả về vị trí hiện tại trên bàn cờ (0 - 39)
        return 0;
    }

    public synchronized void setPosition(int position) {
        // TODO: Cập nhật vị trí mới trên bàn cờ (chuẩn hóa mod 40)
    }

    public synchronized PlayerStatus getStatus() {
        // TODO: Trả về trạng thái người chơi (ACTIVE, IN_JAIL, BANKRUPT)
        return null;
    }

    public synchronized void setStatus(PlayerStatus status) {
        // TODO: Cập nhật trạng thái người chơi
    }

    public synchronized boolean isBot() {
        // TODO: Trả về true nếu người chơi này là bot
        return false;
    }

    public synchronized void setBot(boolean bot) {
        // TODO: Đặt trạng thái bot
    }

    public synchronized int getTurnsInJail() {
        // TODO: Trả về số lượt đã thụ án trong tù
        return 0;
    }

    public synchronized void setTurnsInJail(int turnsInJail) {
        // TODO: Cập nhật số lượt ở trong tù
    }

    public synchronized void addMoney(int amount) {
        // TODO: Cộng thêm tiền vào tài khoản nếu amount > 0
    }

    public synchronized boolean deductMoney(int amount) {
        // TODO: Khấu trừ tiền nếu người chơi đủ số dư; trả về true nếu thành công, false nếu không đủ tiền
        return false;
    }

    public synchronized boolean pay(int amount, Player receiver) {
        // TODO: Chuyển tiền an toàn từ người chơi hiện tại sang receiver (chỉ trừ tiền khi chuyển thành công)
        return false;
    }

    public synchronized void buyProperty(PropertyTile property) {
        // TODO: Kiểm tra số dư, trừ tiền mua và thêm property vào danh sách sở hữu ownedProperties
    }

    public synchronized void transferProperty(PropertyTile property, Player newOwner) {
        // TODO: Chuyển quyền sở hữu của một bất động sản sang người chơi mới
    }

    public synchronized void declareBankruptcy(Player creditor) {
        // TODO: Chuyển trạng thái sang BANKRUPT, bàn giao toàn bộ tài sản cho chủ nợ (nếu có), xóa tiền về 0
    }

    public synchronized int getRailroadCount() {
        // TODO: Đếm số lượng nhà ga đang sở hữu
        return 0;
    }

    public synchronized int getUtilityCount() {
        // TODO: Đếm số lượng tiện ích (Điện, Nước) đang sở hữu
        return 0;
    }

    public synchronized List<PropertyTile> getOwnedProperties() {
        // TODO: Trả về danh sách các bất động sản đang sở hữu
        return null;
    }

    public Inventory getInventory() {
        // TODO: Trả về túi đồ của người chơi
        return null;
    }
}
