package engine.bot;

import engine.GameEngine;
import model.GameRoom;
import model.Player;
import model.RoomStatus;

import java.util.OptionalInt;

/**
 * Controller tự động cho người chơi bot hoặc khi người chơi timeout / AFK.
 * <p>
 * Mọi hành động đều đi qua command của engine (rollDice, payBail, buyProperty, endTurn)
 * nên chịu cùng validate (đúng lượt, đúng phase, turnId) như người chơi thật.
 * <p>
 * Luồng một lượt:
 * 1. AWAITING_ROLL: nếu đang ở tù và dư dả thì nộp bảo lãnh, sau đó gieo xúc xắc.
 * 2. AWAITING_DECISION: mua đất nếu số dư >= RICH_MULTIPLIER lần giá, rồi kết thúc lượt.
 * 3. Phase khác hoặc lệnh bị từ chối: cố kết thúc lượt để không làm treo ván.
 * <p>
 * Chỉ được gọi từ bên trong engine hoặc bởi timer; hàm tĩnh tự giữ monitor của engine.
 */
public class BotController implements BotStrategy {

    /** Bot chỉ mua đất / nộp bảo lãnh khi số dư >= hệ số này nhân với chi phí. */
    public static final int RICH_MULTIPLIER = 3;

    /** Đủ cho tối đa 3 lần gieo (đổ đôi) cùng các bước quyết định trong một lượt. */
    private static final int MAX_STEPS = 8;

    /** Thay mặt người chơi đang có lượt trong phòng của engine. */
    public static void processBotTurn(GameEngine engine) {
        synchronized (engine) { // cùng monitor với các method synchronized của engine
            Player current = engine.getRoom().getCurrentPlayer();
            if (current != null) {
                new BotController().playTurn(engine, current);
            }
        }
    }

    @Override
    public void playTurn(GameEngine engine, Player player) {
        String id = player.getId();
        GameRoom room = engine.getRoom();
        int turnId = room.getTurnId();

        for (int i = 0; i < MAX_STEPS; i++) {
            if (isTurnOver(room, turnId)) {
                return;
            }

            boolean progressed;
            switch (engine.getCurrentPhase()) {
                case AWAITING_ROLL -> progressed = handleRoll(engine, player, id, turnId);
                case AWAITING_DECISION -> progressed = handleDecision(engine, player, id, turnId);
                default -> progressed = false; // phase lạ: dừng vòng lặp, dùng fallback bên dưới
            }

            if (!progressed) {
                break;
            }
        }

        // Lưới an toàn: nếu lượt vẫn chưa chuyển thì cố kết thúc để không treo ván.
        if (!isTurnOver(room, turnId)) {
            engine.endTurn(id, turnId);
        }
    }

    private boolean handleRoll(GameEngine engine, Player player, String id, int turnId) {
        if (player.isInJail() && player.getMoney() >= RICH_MULTIPLIER * GameEngine.BAIL_AMOUNT) {
            engine.payBail(id, turnId); // thất bại thì vẫn gieo xúc xắc như luật tù
        }
        return engine.rollDice(id, turnId);
    }

    private boolean handleDecision(GameEngine engine, Player player, String id, int turnId) {
        OptionalInt price = engine.buyablePrice();
        if (price.isPresent() && player.getMoney() >= RICH_MULTIPLIER * price.getAsInt()) {
            engine.buyProperty(id, turnId, player.getPosition());
        }
        return engine.endTurn(id, turnId);
    }

    private boolean isTurnOver(GameRoom room, int turnId) {
        return room.getTurnId() != turnId || room.getStatus() != RoomStatus.PLAYING;
    }
}
