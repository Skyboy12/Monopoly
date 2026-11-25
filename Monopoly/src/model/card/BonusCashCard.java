package model.card;

import model.GameRoom;
import model.Player;

/**
 * Thẻ thưởng tiền mặt ($50 - $150) khi rút bài.
 */
public class BonusCashCard extends Card {
    private final int amount;

    public BonusCashCard(String id, String description, int amount) {
        super(id, description);
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public void apply(Player player, GameRoom room) {
        if (player != null && amount > 0) {
            player.addMoney(amount);
        }
    }
}
