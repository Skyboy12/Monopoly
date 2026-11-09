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
        tiles.add(new StreetProperty(1, "Mediterranean Avenue", 60, "BROWN", 2));
        tiles.add(new FreeParkingTile(2, "Community Chest 1"));
        tiles.add(new StreetProperty(3, "Baltic Avenue", 60, "BROWN", 4));
        tiles.add(new TaxTile(4, "Income Tax", 200));
        tiles.add(new RailroadProperty(5, "Reading Railroad", 200));
        tiles.add(new StreetProperty(6, "Oriental Avenue", 100, "LIGHT_BLUE", 6));
        tiles.add(new FreeParkingTile(7, "Chance 1"));
        tiles.add(new StreetProperty(8, "Vermont Avenue", 100, "LIGHT_BLUE", 6));
        tiles.add(new StreetProperty(9, "Connecticut Avenue", 120, "LIGHT_BLUE", 8));

        // Cạnh 2: Vị trí 10 - 19
        tiles.add(new JailTile(10, "Jail / Just Visiting"));
        tiles.add(new StreetProperty(11, "St. Charles Place", 140, "PINK", 10));
        tiles.add(new UtilityProperty(12, "Electric Company", 150));
        tiles.add(new StreetProperty(13, "States Avenue", 140, "PINK", 10));
        tiles.add(new StreetProperty(14, "Virginia Avenue", 160, "PINK", 12));
        tiles.add(new RailroadProperty(15, "Pennsylvania Railroad", 200));
        tiles.add(new StreetProperty(16, "St. James Place", 180, "ORANGE", 14));
        tiles.add(new FreeParkingTile(17, "Community Chest 2"));
        tiles.add(new StreetProperty(18, "Tennessee Avenue", 180, "ORANGE", 14));
        tiles.add(new StreetProperty(19, "New York Avenue", 200, "ORANGE", 16));

        // Cạnh 3: Vị trí 20 - 29
        tiles.add(new FreeParkingTile(20, "Free Parking"));
        tiles.add(new StreetProperty(21, "Kentucky Avenue", 220, "RED", 18));
        tiles.add(new FreeParkingTile(22, "Chance 2"));
        tiles.add(new StreetProperty(23, "Indiana Avenue", 220, "RED", 18));
        tiles.add(new StreetProperty(24, "Illinois Avenue", 240, "RED", 20));
        tiles.add(new RailroadProperty(25, "B&O Railroad", 200));
        tiles.add(new StreetProperty(26, "Atlantic Avenue", 260, "YELLOW", 22));
        tiles.add(new StreetProperty(27, "Ventnor Avenue", 260, "YELLOW", 22));
        tiles.add(new UtilityProperty(28, "Water Works", 150));
        tiles.add(new StreetProperty(29, "Marvin Gardens", 280, "YELLOW", 24));

        // Cạnh 4: Vị trí 30 - 39
        tiles.add(new GoToJailTile(30, "Go To Jail"));
        tiles.add(new StreetProperty(31, "Pacific Avenue", 300, "GREEN", 26));
        tiles.add(new StreetProperty(32, "North Carolina Avenue", 300, "GREEN", 26));
        tiles.add(new FreeParkingTile(33, "Community Chest 3"));
        tiles.add(new StreetProperty(34, "Pennsylvania Avenue", 320, "GREEN", 28));
        tiles.add(new RailroadProperty(35, "Short Line Railroad", 200));
        tiles.add(new FreeParkingTile(36, "Chance 3"));
        tiles.add(new StreetProperty(37, "Park Place", 350, "DARK_BLUE", 35));
        tiles.add(new TaxTile(38, "Luxury Tax", 100));
        tiles.add(new StreetProperty(39, "Boardwalk", 400, "DARK_BLUE", 50));
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