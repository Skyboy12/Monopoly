package model;

import java.security.SecureRandom;

/**
 * Xúc xắc được sinh ngẫu nhiên bảo mật từ server (Authoritative Server).
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * Dùng SecureRandom để sinh ngẫu nhiên dice1 và dice2 trong khoảng [1, 6].
 * Client tuyệt đối không được tự gửi giá trị xúc xắc lên.
 */
public class Dice {
    private final SecureRandom random = new SecureRandom();
    private int dice1;
    private int dice2;
    private boolean forced = false;

    public void roll() {
        // Sinh ngẫu nhiên dice1, dice2 từ 1 đến 6 bằng SecureRandom
        this.forced = false;
        this.dice1 = random.nextInt(6) + 1;
        this.dice2 = random.nextInt(6) + 1;
    }

    /**
     * Thiết lập giá trị xúc xắc cố định (dùng cho MagicDiceItem) sao cho tổng bằng total
     * và mỗi viên nằm trong khoảng [1, 6].
     */
    public void setForcedTotal(int total) {
        if (total < 2 || total > 12) {
            roll();
            return;
        }
        int minD1 = Math.max(1, total - 6);
        int maxD1 = Math.min(6, total - 1);
        this.dice1 = minD1 + random.nextInt(maxD1 - minD1 + 1);
        this.dice2 = total - this.dice1;
        this.forced = true;
    }

    public int getDice1() {
        return this.dice1;
    }

    public int getDice2() {
        return this.dice2;
    }

    public int getTotal() {
        return this.dice1 + this.dice2;
    }

    public boolean isForced() {
        return this.forced;
    }

    public boolean isDouble() {
        return !this.forced && this.dice1 > 0 && this.dice1 == this.dice2;
    }
}