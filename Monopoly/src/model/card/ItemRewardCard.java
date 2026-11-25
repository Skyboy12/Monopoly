package model.card;

import model.GameRoom;
import model.Player;
import model.item.ItemFactory;

/**
 * Thẻ thưởng vật phẩm ngẫu nhiên vào túi đồ người chơi.
 */
public class ItemRewardCard extends Card {

    public ItemRewardCard(String id, String description) {
        super(id, description);
    }

    @Override
    public void apply(Player player, GameRoom room) {
        if (player != null && player.getInventory() != null) {
            player.getInventory().addItem(ItemFactory.createRandomItem());
        }
    }
}
