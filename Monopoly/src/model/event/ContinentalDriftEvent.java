package model.event;

import model.GameRoom;
import model.Player;
import model.tile.PropertyTile;
import model.tile.Tile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Sự kiện Toàn cục - Đảo Lộn Đất Đai:
 * Tráo đổi ngẫu nhiên quyền sở hữu 2 ô đất giữa 2 người chơi khác nhau.
 */
public class ContinentalDriftEvent extends GameEvent {
    private static final SecureRandom RANDOM = new SecureRandom();

    public ContinentalDriftEvent() {
        super(
                "CONTINENTAL_DRIFT",
                "Đảo Lộn Đất Đai",
                "Tráo đổi ngẫu nhiên quyền sở hữu 2 ô đất giữa 2 người chơi khác nhau.",
                0
        );
    }

    @Override
    public void onTrigger(GameRoom room) {
        if (room == null || room.getBoard() == null) {
            return;
        }
        List<PropertyTile> ownedProperties = new ArrayList<>();
        for (Tile tile : room.getBoard().getTiles()) {
            if (tile instanceof PropertyTile) {
                PropertyTile prop = (PropertyTile) tile;
                if (prop.hasOwner()) {
                    ownedProperties.add(prop);
                }
            }
        }
        if (ownedProperties.size() < 2) {
            return;
        }

        PropertyTile first = ownedProperties.get(RANDOM.nextInt(ownedProperties.size()));
        List<PropertyTile> differentOwnerProps = new ArrayList<>();
        for (PropertyTile prop : ownedProperties) {
            if (prop.getOwner() != first.getOwner()) {
                differentOwnerProps.add(prop);
            }
        }
        if (differentOwnerProps.isEmpty()) {
            return;
        }

        PropertyTile second = differentOwnerProps.get(RANDOM.nextInt(differentOwnerProps.size()));
        Player owner1 = first.getOwner();
        Player owner2 = second.getOwner();

        owner1.transferProperty(first, owner2);
        owner2.transferProperty(second, owner1);
    }

    @Override
    public void onExpire(GameRoom room) {
        // Sự kiện tức thời, không cần dọn dẹp
    }
}
