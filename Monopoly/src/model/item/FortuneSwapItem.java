package model.item;

import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.tile.PropertyTile;
import model.tile.StreetProperty;

/**
 * Gậy Đổi Vận (Fortune Swap):
 * Hoán đổi vị trí trên bàn cờ với người chơi giàu nhất bàn (pha AWAITING_DECISION).
 */
public class FortuneSwapItem extends Item {

    public FortuneSwapItem() {
        super(
                "FORTUNE_SWAP",
                "Gậy Đổi Vận",
                "Hoán đổi vị trí trên bàn cờ với người chơi có tổng tài sản cao nhất.",
                ItemTriggerPhase.AWAITING_DECISION
        );
    }

    private int calculateTotalWealth(Player player) {
        int wealth = player.getMoney();
        for (PropertyTile prop : player.getOwnedProperties()) {
            wealth += prop.isMortgaged() ? (prop.getPrice() / 2) : prop.getPrice();
            if (prop instanceof StreetProperty) {
                StreetProperty sp = (StreetProperty) prop;
                int buildingCost = sp.getBuildingCost();
                if (sp.isHasHotel()) {
                    wealth += 5 * buildingCost;
                } else {
                    wealth += sp.getHouseCount() * buildingCost;
                }
            }
        }
        return wealth;
    }

    private Player findRichestOther(Player user, GameRoom room) {
        Player richest = null;
        int maxWealth = Integer.MIN_VALUE;
        for (Player p : room.getPlayers()) {
            if (p == user || p.getStatus() != PlayerStatus.ACTIVE) {
                continue;
            }
            int wealth = calculateTotalWealth(p);
            if (wealth > maxWealth) {
                maxWealth = wealth;
                richest = p;
            }
        }
        return richest;
    }

    @Override
    public boolean canUse(Player user, GameRoom room, ItemTargetContext context) {
        if (user == null || room == null) {
            return false;
        }
        Player richest = findRichestOther(user, room);
        return richest != null && calculateTotalWealth(richest) > calculateTotalWealth(user);
    }

    @Override
    public boolean applyEffect(Player user, GameRoom room, ItemTargetContext context) {
        if (!canUse(user, room, context)) {
            return false;
        }
        Player richest = findRichestOther(user, room);
        int userPos = user.getPosition();
        user.setPosition(richest.getPosition());
        richest.setPosition(userPos);
        return true;
    }
}
