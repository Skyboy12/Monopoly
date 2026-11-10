package engine.bot;

import engine.GameEngine;
import model.Player;

/**
 * Chiến lược ra quyết định cho một lượt chơi tự động (bot hoặc người chơi AFK/timeout).
 */
public interface BotStrategy {
    /**
     * Thực hiện toàn bộ lượt của {@code player}. Phải đi qua các command của engine
     * và chỉ được gọi khi engine đang giữ lock.
     */
    void playTurn(GameEngine engine, Player player);
}
