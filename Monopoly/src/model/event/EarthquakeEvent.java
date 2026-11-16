package model.event;

import model.GameRoom;
import model.tile.StreetProperty;
import model.tile.Tile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sự kiện Cục bộ - Động Đất Cục Bộ:
 * Sập 1 cấp nhà trên một khu phố (colorGroup) ngẫu nhiên.
 */
public class EarthquakeEvent extends GameEvent {
    private static final SecureRandom RANDOM = new SecureRandom();

    public EarthquakeEvent() {
        super(
                "EARTHQUAKE",
                "Động Đất Cục Bộ",
                "Một khu phố ngẫu nhiên bị chấn động, mỗi ô đất trong khu phố bị sập 1 cấp nhà.",
                0
        );
    }

    @Override
    public void onTrigger(GameRoom room) {
        if (room == null || room.getBoard() == null) {
            return;
        }
        Map<String, List<StreetProperty>> groupsWithBuildings = new HashMap<>();
        for (Tile tile : room.getBoard().getTiles()) {
            if (tile instanceof StreetProperty) {
                StreetProperty street = (StreetProperty) tile;
                if (street.getHouseCount() > 0 || street.isHasHotel()) {
                    groupsWithBuildings
                            .computeIfAbsent(street.getColorGroup(), k -> new ArrayList<>())
                            .add(street);
                }
            }
        }
        if (groupsWithBuildings.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>(groupsWithBuildings.keySet());
        String chosenGroup = keys.get(RANDOM.nextInt(keys.size()));
        for (StreetProperty street : groupsWithBuildings.get(chosenGroup)) {
            if (street.isHasHotel()) {
                street.setHasHotel(false);
                street.setHouseCount(4);
            } else if (street.getHouseCount() > 0) {
                street.setHouseCount(street.getHouseCount() - 1);
            }
        }
    }

    @Override
    public void onExpire(GameRoom room) {
        // Sự kiện tức thời, không cần dọn dẹp
    }
}
