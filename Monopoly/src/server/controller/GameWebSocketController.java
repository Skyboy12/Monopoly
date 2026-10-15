package server.controller;

import engine.GameEngine;
import network.GameMessage;

/**
 * Controller tiếp nhận và xử lý các WebSocket / STOMP command từ Client.
 * Tham khảo docs/workflow.md Mục 4.3 & docs/member-4-backend-platform.md:
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI (WEBSOCKET / STOMP COMMANDS):
 * ════════════════════════════════════════════════════════════════════════════════
 * - Client gửi command lên Server qua destination "/app/room/{roomId}/...".
 * - Server Authoritative:
 * 1. Kiểm tra JWT Token của người gửi.
 * 2. Kiểm tra turnId gửi lên có khớp với turnId hiện tại của phòng không (chống
 * stale/replay command).
 * 3. Kiểm tra phase hiện tại của phòng (TurnPhase) xem có được phép thực hiện
 * hành vi này không.
 * - Sau khi xử lý thành công, broadcast sự kiện mới tới topic
 * "/topic/room/{roomId}":
 * - DICE_ROLLED: Khi xúc xắc được gieo và người chơi di chuyển.
 * - PROPERTY_BOUGHT: Khi người chơi mua đất.
 * - TURN_CHANGED: Khi chuyển lượt cho người chơi tiếp theo.
 * - ITEM_USED: Khi người chơi sử dụng vật phẩm.
 */
public class GameWebSocketController {

    /**
     * Endpoint: /app/room/{roomId}/roll-dice
     * Yêu cầu gieo xúc xắc từ người chơi có lượt.
     */
    public GameMessage handleRollDice(String roomId, String playerId, int turnId, GameEngine engine) {
        // TODO:
        // 1. Kiểm tra engine != null và đúng lượt của playerId
        // 2. Gọi engine.rollDice(playerId, turnId)
        // 3. Nếu thành công -> tạo GameMessage sự kiện DICE_ROLLED để broadcast cho cả
        // phòng
        // 4. Nếu thất bại -> gửi thông báo lỗi riêng cho người chơi (qua
        // /queue/player/{userId}/errors)
        return null;
    }

    /**
     * Endpoint: /app/room/{roomId}/buy-property
     * Quyết định mua ô đất vừa dừng chân.
     */
    public GameMessage handleBuyProperty(String roomId, String playerId, int turnId, int tileIndex, GameEngine engine) {
        // TODO:
        // 1. Kiểm tra người chơi đang ở ô tileIndex và đủ tiền mua
        // 2. Thực hiện trừ tiền và gán quyền sở hữu ô đất cho người chơi
        // 3. Broadcast sự kiện PROPERTY_BOUGHT
        return null;
    }

    /**
     * Endpoint: /app/room/{roomId}/use-item
     * Kích hoạt một vật phẩm từ túi đồ.
     */
    public GameMessage handleUseItem(String roomId, String playerId, int turnId, String itemId, GameEngine engine) {
        // TODO:
        // 1. Kiểm tra người chơi có sở hữu itemId trong túi đồ không
        // 2. Kiểm tra điều kiện canUse(...) của item
        // 3. Tiêu hao item và gọi applyEffect(...)
        // 4. Broadcast sự kiện ITEM_USED
        return null;
    }

    /**
     * Endpoint: /app/room/{roomId}/end-turn
     * Kết thúc lượt đi và chuyển giao quyền điều khiển.
     */
    public GameMessage handleEndTurn(String roomId, String playerId, int turnId, GameEngine engine) {
        // TODO:
        // 1. Gọi engine.endTurn(playerId, turnId)
        // 2. Broadcast sự kiện TURN_CHANGED kèm theo playerId mới và turnId mới
        return null;
    }
}
