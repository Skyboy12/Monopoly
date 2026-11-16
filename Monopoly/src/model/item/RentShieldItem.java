package model.item;

import model.GameRoom;
import model.Player;
import model.tile.PropertyTile;
import model.tile.Tile;

/**
 * Khiên Hộ mệnh (Rent Shield):
 * Miễn 100% tiền thuê khi người chơi vừa dẫm vào ô đất thuộc sở hữu của đối thủ (pha ON_LAND).
 */
public class RentShieldItem extends Item {

    public RentShieldItem() {
        super(
                "RENT_SHIELD",
                "Khiên Hộ mệnh",
                "Miễn 100% tiền thuê khi dẫm vào ô đất hoặc khách sạn của đối thủ.",
                ItemTriggerPhase.ON_LAND
        );
    }

    @Override
    public boolean canUse(Player user, GameRoom room, ItemTargetContext context) {
        if (user == null || room == null || room.getBoard() == null) {
            return false;
        }
        Tile currentTile = room.getBoard().getTile(user.getPosition());
        if (!(currentTile instanceof PropertyTile)) {
            return false;
        }
        PropertyTile prop = (PropertyTile) currentTile;
        return prop.hasOwner() && prop.getOwner() != user && !prop.isMortgaged();
    }

    @Override
    public boolean applyEffect(Player user, GameRoom room, ItemTargetContext context) {
        if (!canUse(user, room, context)) {
            return false;
        }
        user.setRentShieldActive(true);
        return true;
    }
}
