package model.item;

import model.GameRoom;
import model.Player;
import model.tile.StreetProperty;
import model.tile.Tile;

/**
 * Thùng Thuốc nổ (Dynamite):
 * Thổi bay toàn bộ nhà/khách sạn trên 1 ô đất chỉ định (pha AWAITING_DECISION).
 */
public class DynamiteItem extends Item {

    public DynamiteItem() {
        super(
                "DYNAMITE",
                "Thùng Thuốc nổ",
                "Phá hủy toàn bộ nhà và khách sạn trên một ô đất chỉ định.",
                ItemTriggerPhase.AWAITING_DECISION
        );
    }

    @Override
    public boolean canUse(Player user, GameRoom room, ItemTargetContext context) {
        if (user == null || room == null || room.getBoard() == null || context == null
                || context.getTargetTileIndex() == null) {
            return false;
        }
        Tile tile = room.getBoard().getTile(context.getTargetTileIndex());
        if (!(tile instanceof StreetProperty)) {
            return false;
        }
        StreetProperty street = (StreetProperty) tile;
        return street.getHouseCount() > 0 || street.isHasHotel();
    }

    @Override
    public boolean applyEffect(Player user, GameRoom room, ItemTargetContext context) {
        if (!canUse(user, room, context)) {
            return false;
        }
        StreetProperty street = (StreetProperty) room.getBoard().getTile(context.getTargetTileIndex());
        street.setHouseCount(0);
        street.setHasHotel(false);
        return true;
    }
}
