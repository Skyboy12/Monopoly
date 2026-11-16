package model.event;

import model.GameRoom;
import model.Player;
import model.PlayerStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Sự kiện Toàn cục - Thuế Nhà Giàu Siêu Cấp:
 * Trừ 40% tiền mặt của người giàu nhất chia đều cho những người chơi khác.
 */
public class RichTaxEvent extends GameEvent {

    public RichTaxEvent() {
        super(
                "RICH_TAX",
                "Thuế Nhà Giàu Siêu Cấp",
                "Người chơi có nhiều tiền mặt nhất bị trừ 40% tiền mặt chia đều cho các người chơi còn lại.",
                0
        );
    }

    @Override
    public void onTrigger(GameRoom room) {
        if (room == null) {
            return;
        }
        List<Player> activePlayers = new ArrayList<>();
        for (Player p : room.getPlayers()) {
            if (p.getStatus() != PlayerStatus.BANKRUPT) {
                activePlayers.add(p);
            }
        }
        if (activePlayers.size() < 2) {
            return;
        }

        Player richest = activePlayers.get(0);
        for (Player p : activePlayers) {
            if (p.getMoney() > richest.getMoney()) {
                richest = p;
            }
        }

        int taxAmount = (int) (richest.getMoney() * 0.40);
        if (taxAmount <= 0 || !richest.deductMoney(taxAmount)) {
            return;
        }

        int otherCount = activePlayers.size() - 1;
        int share = taxAmount / otherCount;
        for (Player p : activePlayers) {
            if (p != richest) {
                p.addMoney(share);
            }
        }
    }

    @Override
    public void onExpire(GameRoom room) {
        // Sự kiện tức thời, không cần dọn dẹp
    }
}
