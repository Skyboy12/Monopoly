package model;

import java.security.SecureRandom;

/**
 * Xúc xắc được sinh ngẫu nhiên bảo mật từ server (Authoritative Server).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Dùng SecureRandom để sinh ngẫu nhiên dice1 và dice2 trong khoảng [1, 6].
 * - Client tuyệt đối không được tự gửi giá trị xúc xắc lên.
 */
public class Dice {
    private final SecureRandom random = new SecureRandom();
    private int dice1;
    private int dice2;

    public void roll() {
        // TODO: Sinh ngẫu nhiên dice1, dice2 từ 1 đến 6 bằng SecureRandom
    }

    public int getDice1() {
        // TODO: Trả về giá trị của xúc xắc thứ nhất
        return 0;
    }

    public int getDice2() {
        // TODO: Trả về giá trị của xúc xắc thứ hai
        return 0;
    }

    public int getTotal() {
        // TODO: Trả về tổng điểm 2 xúc xắc (dice1 + dice2)
        return 0;
    }

    public boolean isDouble() {
        // TODO: Kiểm tra xem 2 xúc xắc có cùng giá trị không (đổ đôi)
        return false;
    }
}
