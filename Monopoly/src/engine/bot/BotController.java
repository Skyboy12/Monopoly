package engine.bot;

import engine.GameEngine;

/**
 * Controller tự động cho người chơi bot hoặc khi người chơi timeout.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Khi người chơi AFK / Timeout hoặc là Bot, BotController thay mặt thực hiện:
 *   1. Gieo xúc xắc nếu đang ở phase AWAITING_ROLL.
 *   2. Đưa ra quyết định mua đất nếu đủ tiền (VD: số dư >= 3 lần giá mua).
 *   3. Kết thúc lượt để chuyển quyền cho người tiếp theo, không làm treo ván chơi.
 */
public class BotController {

    public static void processBotTurn(GameEngine engine) {
        // TODO:
        // 1. Lấy người chơi hiện tại từ engine.getRoom()
        // 2. Tự động gọi engine.rollDice(...) nếu đang ở phase AWAITING_ROLL
        // 3. Tự động mua đất nếu đang ở ô đất trống và đủ tiền
        // 4. Gọi engine.endTurn(...) để hoàn tất lượt
    }
}
