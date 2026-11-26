package model.item;

import model.GameRoom;
import model.Player;
import model.tile.PropertyTile;
import model.tile.StreetProperty;
import model.tile.Tile;

/**
 * Cưỡng chế Thu mua (Hostile Takeover):
 * Trả tiền mua đứt 1 ô đất bất kỳ đang thuộc sở hữu của đối thủ (pha AWAITING_DECISION).
 */
public class HostileTakeoverItem extends Item {

    public HostileTakeoverItem() {
        super(
                "HOSTILE_TAKEOVER",
                "Cưỡng chế Thu mua",
                "Trả tiền niêm yết để mua đứt 1 ô đất bất kỳ của đối thủ.",
                ItemTriggerPhase.PRE_ROLL_OR_DECISION
        );
    }

    public int getTakeoverCost(PropertyTile prop) {
        if (prop == null) {
            return 0;
        }
        int buildingValue = 0;
        if (prop instanceof StreetProperty) {
            StreetProperty sp = (StreetProperty) prop;
            int buildingCost = sp.getBuildingCost();
            buildingValue = sp.isHasHotel() ? (5 * buildingCost) : (sp.getHouseCount() * buildingCost);
        }
        return prop.getPrice() + buildingValue;
    }

    @Override
    public boolean canUse(Player user, GameRoom room, ItemTargetContext context) {
        if (user == null || room == null || room.getBoard() == null || context == null
                || context.getTargetTileIndex() == null) {
            return false;
        }
        Tile tile = room.getBoard().getTile(context.getTargetTileIndex());
        if (!(tile instanceof PropertyTile)) {
            return false;
        }
        PropertyTile prop = (PropertyTile) tile;
        return prop.hasOwner() && prop.getOwner() != user && user.getMoney() >= getTakeoverCost(prop);
    }

    @Override
    public boolean applyEffect(Player user, GameRoom room, ItemTargetContext context) {
        if (!canUse(user, room, context)) {
            return false;
        }
        PropertyTile prop = (PropertyTile) room.getBoard().getTile(context.getTargetTileIndex());
        Player oldOwner = prop.getOwner();
        if (!user.pay(getTakeoverCost(prop), oldOwner)) {
            return false;
        }
        oldOwner.transferProperty(prop, user);
        return true;
    }
}
