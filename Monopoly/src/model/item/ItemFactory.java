package model.item;

import java.security.SecureRandom;

/**
 * Factory tạo ngẫu nhiên một vật phẩm trong hệ thống Party Mode.
 */
public final class ItemFactory {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ItemFactory() {
    }

    public static Item createRandomItem() {
        int pick = RANDOM.nextInt(6);
        switch (pick) {
            case 0:
                return new MagicDiceItem();
            case 1:
                return new RentShieldItem();
            case 2:
                return new HostileTakeoverItem();
            case 3:
                return new DynamiteItem();
            case 4:
                return new ArrestWarrantItem();
            default:
                return new FortuneSwapItem();
        }
    }
}
