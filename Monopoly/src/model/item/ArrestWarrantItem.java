package model.item;

import model.GameRoom;
import model.Player;
import model.PlayerStatus;

/**
 * Lệnh Bắt Khẩn cấp (Arrest Warrant):
 * Tống thẳng 1 người chơi bất kỳ vào tù ngay lập tức (pha AWAITING_DECISION).
 */
public class ArrestWarrantItem extends Item {
    private static final int JAIL_POSITION = 10;

    public ArrestWarrantItem() {
        super(
                "ARREST_WARRANT",
                "Lệnh Bắt Khẩn cấp",
                "Đưa thẳng một người chơi đang hoạt động vào tù ngay lập tức.",
                ItemTriggerPhase.PRE_ROLL_OR_DECISION
        );
    }

    private Player findTargetPlayer(GameRoom room, String targetPlayerId) {
        if (room == null || targetPlayerId == null) {
            return null;
        }
        for (Player p : room.getPlayers()) {
            if (p.getId().equals(targetPlayerId)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean canUse(Player user, GameRoom room, ItemTargetContext context) {
        if (user == null || room == null || context == null || context.getTargetPlayerId() == null) {
            return false;
        }
        Player target = findTargetPlayer(room, context.getTargetPlayerId());
        return target != null && target != user && target.getStatus() == PlayerStatus.ACTIVE;
    }

    @Override
    public boolean applyEffect(Player user, GameRoom room, ItemTargetContext context) {
        if (!canUse(user, room, context)) {
            return false;
        }
        Player target = findTargetPlayer(room, context.getTargetPlayerId());
        target.sendToJail(JAIL_POSITION);
        return true;
    }
}
