package model.item;

import model.GameRoom;
import model.Player;

/**
 * Xúc xắc Thần kỳ (Magic Dice):
 * Cho phép người chơi chọn chính xác số bước di chuyển (từ 2 đến 12) trong pha PRE_ROLL.
 */
public class MagicDiceItem extends Item {

    public MagicDiceItem() {
        super(
                "MAGIC_DICE",
                "Xúc xắc Thần kỳ",
                "Chọn chính xác số bước di chuyển từ 2 đến 12 trước khi gieo xúc xắc.",
                ItemTriggerPhase.PRE_ROLL
        );
    }

    @Override
    public boolean canUse(Player user, GameRoom room, ItemTargetContext context) {
        if (user == null || room == null || context == null || context.getDesiredDiceValue() == null) {
            return false;
        }
        int val = context.getDesiredDiceValue();
        return val >= 2 && val <= 12;
    }

    @Override
    public boolean applyEffect(Player user, GameRoom room, ItemTargetContext context) {
        if (!canUse(user, room, context)) {
            return false;
        }
        room.setForcedDiceValue(context.getDesiredDiceValue());
        return true;
    }
}
