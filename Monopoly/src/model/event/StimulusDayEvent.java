package model.event;

import model.GameRoom;
import model.Player;
import model.PlayerStatus;

/**
 * Sự kiện Toàn cục - Ngày Phát Tiền Cứu Trợ:
 * Ngân hàng tặng mỗi người chơi $500.
 */
public class StimulusDayEvent extends GameEvent {
    private static final int STIMULUS_AMOUNT = 500_000;

    public StimulusDayEvent() {
        super(
                "STIMULUS_DAY",
                "Ngày Phát Tiền Cứu Trợ",
                "Ngân hàng tự động phát cho mỗi người chơi trong phòng $500,000.",
                0
        );
    }

    @Override
    public void onTrigger(GameRoom room) {
        if (room == null) {
            return;
        }
        for (Player player : room.getPlayers()) {
            if (player.getStatus() != PlayerStatus.BANKRUPT) {
                player.addMoney(STIMULUS_AMOUNT);
            }
        }
    }

    @Override
    public void onExpire(GameRoom room) {
        // Sự kiện tức thời, không cần dọn dẹp
    }
}
