package model.card;

import model.GameRoom;
import model.Player;

/**
 * Lớp trừu tượng đại diện cho thẻ bài Cơ hội / Khí vận (Chance / Community Chest).
 */
public abstract class Card {
    private final String id;
    private final String description;

    public Card(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public abstract void apply(Player player, GameRoom room);
}
