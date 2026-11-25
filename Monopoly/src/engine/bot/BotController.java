package engine.bot;

import engine.GameEngine;
import model.GameRoom;
import model.Player;
import model.RoomStatus;

/**
 * Controller tự động cho người chơi bot hoặc khi người chơi timeout.
 * Ủy quyền toàn bộ quyết định cho {@link BotStrategy} mà không giữ lock {@code room}.
 */
public class BotController {

    private static final BotStrategy DEFAULT_STRATEGY = new DefaultBotStrategy();

    public static void processBotTurn(GameEngine engine) {
        if (engine == null) {
            return;
        }

        GameRoom room = engine.getRoom();
        if (room.getStatus() != RoomStatus.PLAYING) {
            return;
        }

        Player player = room.getCurrentPlayer();
        if (player == null) {
            return;
        }

        DEFAULT_STRATEGY.playTurn(engine, player);
    }
}
