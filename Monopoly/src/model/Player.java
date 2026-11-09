package model;

import model.item.Inventory;
import model.tile.PropertyTile;
import model.tile.RailroadProperty;
import model.tile.UtilityProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thực thể người chơi trong ván cờ.
 *
 * HƯỚNG DẪN TRIỂN KHAI (ĐÓNG GÓI - ENCAPSULATION):
 * Tất cả các trường dữ liệu (tiền, vị trí, tài sản) phải là private.
 * Tuyệt đối không cho phép bên ngoài gán trực tiếp: player.money = ...
 * Mọi biến động tài sản/trạng thái phải thông qua các method nghiệp vụ xác thực.
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
        return this.money;
    }

    public synchronized int getPosition() {
        return this.position;
    }

    public synchronized void setPosition(int position) {
        // Chuẩn hóa vị trí luôn nằm trong khoảng 0 - 39
        this.position = ((position % 40) + 40) % 40;
    }

    public synchronized PlayerStatus getStatus() {
        return this.status;
    }

    public synchronized void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public synchronized boolean isBot() {
        return this.isBot;
    }

    public synchronized void setBot(boolean bot) {
        this.isBot = bot;
    }

    public synchronized int getTurnsInJail() {
        return this.turnsInJail;
    }

    public synchronized void setTurnsInJail(int turnsInJail) {
        this.turnsInJail = Math.max(0, turnsInJail);
    }

    public synchronized void addMoney(int amount) {
        if (amount > 0) {
            this.money += amount;
        }
    }

    public synchronized boolean deductMoney(int amount) {
        if (amount > 0 && this.money >= amount) {
            this.money -= amount;
            return true;
        }
        return false;
    }

    public synchronized boolean pay(int amount, Player receiver) {
        if (amount <= 0) {
            return false;
        }
        if (this.money >= amount) {
            this.money -= amount;
            if (receiver != null) {
                receiver.addMoney(amount);
            }
            return true;
        }
        return false;
    }

    public synchronized void buyProperty(PropertyTile property) {
        if (property == null) return;
        if (property.getOwner() == null && deductMoney(property.getPrice())) {
            this.ownedProperties.add(property);
            property.setOwner(this);
        }
    }

    public synchronized void transferProperty(PropertyTile property, Player newOwner) {
        if (property == null || !this.ownedProperties.contains(property)) {
            return;
        }
        this.ownedProperties.remove(property);
        if (newOwner != null) {
            newOwner.getOwnedProperties().add(property);
            property.setOwner(newOwner);
        } else {
            property.setOwner(null); // Trả lại về ngân hàng
        }
    }

    public synchronized void declareBankruptcy(Player creditor) {
        this.status = PlayerStatus.BANKRUPT;

        // Bàn giao toàn bộ tài sản cho chủ nợ (nếu có) hoặc trả về ngân hàng
        for (PropertyTile prop : new ArrayList<>(this.ownedProperties)) {
            if (creditor != null) {
                creditor.getOwnedProperties().add(prop);
                prop.setOwner(creditor);
            } else {
                prop.setOwner(null);
            }
        }
        this.ownedProperties.clear();

        // Chuyển toàn bộ tiền còn lại cho chủ nợ trước khi xóa về 0
        if (creditor != null && this.money > 0) {
            creditor.addMoney(this.money);
        }
        this.money = 0;
    }

    public synchronized int getRailroadCount() {
        int count = 0;
        for (PropertyTile prop : ownedProperties) {
            if (prop instanceof RailroadProperty) {
                count++;
            }
        }
        return count;
    }

    public synchronized int getUtilityCount() {
        int count = 0;
        for (PropertyTile prop : ownedProperties) {
            if (prop instanceof UtilityProperty) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<PropertyTile> getOwnedProperties() {
        return this.ownedProperties;
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    // --- CÁC HÀM TIỆN ÍCH HỖ TRỢ NGHIỆP VỤ DI CHUYỂN & VÀO TÙ ---

    public synchronized void move(int steps) {
        int oldPosition = this.position;
        setPosition(this.position + steps);

        // Thưởng $200 khi vượt qua hoặc dừng đúng ô GO (index 0) nếu không ở tù
        if (this.position < oldPosition && this.status != PlayerStatus.IN_JAIL) {
            addMoney(200);
        }
    }

    public synchronized void sendToJail(int jailPosition) {
        setPosition(jailPosition);
        this.status = PlayerStatus.IN_JAIL;
        this.turnsInJail = 0;
    }

    public synchronized boolean payBail() {
        if (this.status == PlayerStatus.IN_JAIL && deductMoney(50)) {
            this.status = PlayerStatus.ACTIVE;
            this.turnsInJail = 0;
            return true;
        }
        return false;
    }
}