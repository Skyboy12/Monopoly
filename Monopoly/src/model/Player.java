package model;

import model.item.Inventory;
import model.tile.PropertyTile;
import model.tile.RailroadProperty;
import model.tile.StreetProperty;
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
    public static final int BAIL_AMOUNT = 50_000;

    private final String id;
    private final String name;
    private int money;
    private int position;
    private PlayerStatus status;
    private final List<PropertyTile> ownedProperties;
    private final Inventory inventory;
    private boolean isBot;
    private int turnsInJail;
    private int pendingDebt;
    private Player pendingCreditor;
    private boolean pendingDebtIsBail;

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

    public boolean pay(int amount, Player receiver) {
        if (amount <= 0) {
            return false;
        }
        boolean paid = false;
        synchronized (this) {
            if (this.money >= amount) {
                this.money -= amount;
                paid = true;
            }
        }
        if (paid && receiver != null) {
            receiver.addMoney(amount);
        }
        return paid;
    }

    public synchronized void receivePropertyInternal(PropertyTile prop) {
        if (prop == null) {
            return;
        }
        if (!this.ownedProperties.contains(prop)) {
            this.ownedProperties.add(prop);
        }
        prop.setOwner(this);
    }

    public synchronized void buyProperty(PropertyTile property) {
        if (property == null) return;
        if (property.getOwner() == null && deductMoney(property.getPrice())) {
            this.ownedProperties.add(property);
            property.setOwner(this);
        }
    }

    public void transferProperty(PropertyTile property, Player newOwner) {
        if (property == null) {
            return;
        }
        synchronized (this) {
            if (!this.ownedProperties.contains(property)) {
                return;
            }
            this.ownedProperties.remove(property);
            if (newOwner == null) {
                property.setOwner(null); // Trả lại về ngân hàng
                return;
            }
        }
        if (newOwner == this) {
            receivePropertyInternal(property);
        } else {
            newOwner.receivePropertyInternal(property);
        }
    }

    public void declareBankruptcy(Player creditor) {
        List<PropertyTile> propsToTransfer;
        int remainingMoney;
        synchronized (this) {
            this.status = PlayerStatus.BANKRUPT;
            propsToTransfer = new ArrayList<>(this.ownedProperties);
            this.ownedProperties.clear();
            remainingMoney = this.money;
            this.money = 0;

            if (creditor == null) {
                for (PropertyTile prop : propsToTransfer) {
                    prop.setMortgaged(false);
                    if (prop instanceof StreetProperty) {
                        StreetProperty sp = (StreetProperty) prop;
                        sp.setHouseCount(0);
                        sp.setHasHotel(false);
                    }
                    prop.setOwner(null);
                }
            }
        }

        if (creditor != null) {
            for (PropertyTile prop : propsToTransfer) {
                creditor.receivePropertyInternal(prop);
            }
            if (remainingMoney > 0) {
                creditor.addMoney(remainingMoney);
            }
        }
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
        return Collections.unmodifiableList(new ArrayList<>(this.ownedProperties));
    }

    private boolean rentShieldActive = false;

    public Inventory getInventory() {
        return this.inventory;
    }

    public synchronized boolean isRentShieldActive() {
        return rentShieldActive;
    }

    public synchronized void setRentShieldActive(boolean rentShieldActive) {
        this.rentShieldActive = rentShieldActive;
    }

    // --- CÁC HÀM TIỆN ÍCH HỖ TRỢ NGHIỆP VỤ DI CHUYỂN & VÀO TÙ ---

    public synchronized void move(int steps) {
        int oldPosition = this.position;
        setPosition(this.position + steps);

        // Chỉ thưởng $200 khi đi tiến (steps > 0), không ở tù và thực sự đi qua GO
        if (steps > 0 && this.status != PlayerStatus.IN_JAIL) {
            int totalPos = oldPosition + steps;
            int lapsPassed = totalPos / 40;
            if (this.position == 0 && lapsPassed > 0) {
                lapsPassed--;
            }
            if (lapsPassed > 0) {
                addMoney(200_000 * lapsPassed);
            }
        }
    }

    public synchronized void sendToJail(int jailPosition) {
        setPosition(jailPosition);
        this.status = PlayerStatus.IN_JAIL;
        this.turnsInJail = 0;
    }

    public synchronized boolean payBail() {
        if (this.status == PlayerStatus.IN_JAIL && deductMoney(BAIL_AMOUNT)) {
            this.status = PlayerStatus.ACTIVE;
            this.turnsInJail = 0;
            return true;
        }
        return false;
    }

    public synchronized void registerDebt(int amount, Player creditor, boolean isBail) {
        this.pendingDebt = Math.max(0, amount);
        this.pendingCreditor = creditor;
        this.pendingDebtIsBail = isBail;
    }

    public synchronized boolean hasPendingDebt() {
        return this.pendingDebt > 0;
    }

    public synchronized int getPendingDebt() {
        return this.pendingDebt;
    }

    public synchronized Player getPendingCreditor() {
        return this.pendingCreditor;
    }

    public synchronized boolean isPendingDebtIsBail() {
        return this.pendingDebtIsBail;
    }

    public synchronized void clearDebt() {
        this.pendingDebt = 0;
        this.pendingCreditor = null;
        this.pendingDebtIsBail = false;
    }

    public synchronized int getLiquidationValue() {
        int total = 0;
        for (PropertyTile p : this.ownedProperties) {
            int buildingValue = 0;
            if (p instanceof StreetProperty) {
                StreetProperty sp = (StreetProperty) p;
                int cost = sp.getBuildingCost();
                buildingValue = sp.isHasHotel() ? 5 * cost : sp.getHouseCount() * cost;
            }
            total += (p.getPrice() + buildingValue) / 2;
        }
        return total;
    }
}