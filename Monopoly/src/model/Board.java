package model;

import model.tile.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bàn cờ Monopoly chứa 40 ô cờ tiêu chuẩn.
 */
public class Board {
    private final List<Tile> tiles;

    public Board() {
        this.tiles = new ArrayList<>(40);
        initializeBoard();
    }

    private void initializeBoard() {
        // Cạnh 1: Vị trí 0 - 9
        tiles.add(new GoTile(0, "GO"));
        tiles.add(new StreetProperty(1, "Mediterranean Avenue", 60_000, "BROWN", 2_000));
        tiles.add(new CardTile(2, "Community Chest 1", CardTile.DeckType.COMMUNITY_CHEST));
        tiles.add(new StreetProperty(3, "Baltic Avenue", 60_000, "BROWN", 4_000));
        tiles.add(new TaxTile(4, "Income Tax", 200_000));
        tiles.add(new RailroadProperty(5, "Reading Railroad", 200_000));
        tiles.add(new StreetProperty(6, "Oriental Avenue", 100_000, "LIGHT_BLUE", 6_000));
        tiles.add(new CardTile(7, "Chance 1", CardTile.DeckType.CHANCE));
        tiles.add(new StreetProperty(8, "Vermont Avenue", 100_000, "LIGHT_BLUE", 6_000));
        tiles.add(new StreetProperty(9, "Connecticut Avenue", 120_000, "LIGHT_BLUE", 8_000));

        // Cạnh 2: Vị trí 10 - 19
        tiles.add(new JailTile(10, "Jail / Just Visiting"));
        tiles.add(new StreetProperty(11, "St. Charles Place", 140_000, "PINK", 10_000));
        tiles.add(new UtilityProperty(12, "Electric Company", 150_000));
        tiles.add(new StreetProperty(13, "States Avenue", 140_000, "PINK", 10_000));
        tiles.add(new StreetProperty(14, "Virginia Avenue", 160_000, "PINK", 12_000));
        tiles.add(new RailroadProperty(15, "Pennsylvania Railroad", 200_000));
        tiles.add(new StreetProperty(16, "St. James Place", 180_000, "ORANGE", 14_000));
        tiles.add(new CardTile(17, "Community Chest 2", CardTile.DeckType.COMMUNITY_CHEST));
        tiles.add(new StreetProperty(18, "Tennessee Avenue", 180_000, "ORANGE", 14_000));
        tiles.add(new StreetProperty(19, "New York Avenue", 200_000, "ORANGE", 16_000));

        // Cạnh 3: Vị trí 20 - 29
        tiles.add(new FreeParkingTile(20, "Free Parking"));
        tiles.add(new StreetProperty(21, "Kentucky Avenue", 220_000, "RED", 18_000));
        tiles.add(new CardTile(22, "Chance 2", CardTile.DeckType.CHANCE));
        tiles.add(new StreetProperty(23, "Indiana Avenue", 220_000, "RED", 18_000));
        tiles.add(new StreetProperty(24, "Illinois Avenue", 240_000, "RED", 20_000));
        tiles.add(new RailroadProperty(25, "B&O Railroad", 200_000));
        tiles.add(new StreetProperty(26, "Atlantic Avenue", 260_000, "YELLOW", 22_000));
        tiles.add(new StreetProperty(27, "Ventnor Avenue", 260_000, "YELLOW", 22_000));
        tiles.add(new UtilityProperty(28, "Water Works", 150_000));
        tiles.add(new StreetProperty(29, "Marvin Gardens", 280_000, "YELLOW", 24_000));

        // Cạnh 4: Vị trí 30 - 39
        tiles.add(new GoToJailTile(30, "Go To Jail"));
        tiles.add(new StreetProperty(31, "Pacific Avenue", 300_000, "GREEN", 26_000));
        tiles.add(new StreetProperty(32, "North Carolina Avenue", 300_000, "GREEN", 26_000));
        tiles.add(new CardTile(33, "Community Chest 3", CardTile.DeckType.COMMUNITY_CHEST));
        tiles.add(new StreetProperty(34, "Pennsylvania Avenue", 320_000, "GREEN", 28_000));
        tiles.add(new RailroadProperty(35, "Short Line Railroad", 200_000));
        tiles.add(new CardTile(36, "Chance 3", CardTile.DeckType.CHANCE));
        tiles.add(new StreetProperty(37, "Park Place", 350_000, "DARK_BLUE", 35_000));
        tiles.add(new TaxTile(38, "Luxury Tax", 100_000));
        tiles.add(new StreetProperty(39, "Boardwalk", 400_000, "DARK_BLUE", 50_000));
    }

    public Tile getTile(int index) {
        if (tiles.isEmpty()) {
            return null;
        }
        int normalizedIndex = ((index % 40) + 40) % 40;
        return tiles.get(normalizedIndex);
    }

    public List<Tile> getTiles() {
        return Collections.unmodifiableList(this.tiles);
    }
}