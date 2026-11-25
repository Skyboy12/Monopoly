package model.tile;

import model.GameRoom;
import model.Player;
import model.card.BonusCashCard;
import model.card.Card;
import model.card.ItemRewardCard;

import java.security.SecureRandom;
import java.util.List;

/**
 * Ô Thẻ Cơ hội (CHANCE) / Khí vận (COMMUNITY_CHEST).
 */
public class CardTile extends Tile {

    public enum DeckType {
        CHANCE,
        COMMUNITY_CHEST
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<Card> CHANCE_DECK = List.of(
            new BonusCashCard("CHANCE_DIVIDEND", "Ngân hàng chia cổ tức $50.", 50),
            new BonusCashCard("CHANCE_INVESTMENT", "Đầu tư sinh lời, nhận $150.", 150),
            new ItemRewardCard("CHANCE_MYSTERY_BOX", "Nhặt được rương vật phẩm ngẫu nhiên.")
    );

    private static final List<Card> COMMUNITY_CHEST_DECK = List.of(
            new BonusCashCard("CHEST_REFUND", "Hoàn thuế thu nhập, nhận $100.", 100),
            new BonusCashCard("CHEST_PRIZE", "Giải nhì cuộc thi sắc đẹp, nhận $50.", 50),
            new ItemRewardCard("CHEST_SUPPLY_DROP", "Nhận tiếp tế một vật phẩm ngẫu nhiên.")
    );

    private final DeckType deckType;

    public CardTile(int index, String name, DeckType deckType) {
        super(index, name);
        this.deckType = deckType;
    }

    public DeckType getDeckType() {
        return deckType;
    }

    @Override
    public void onLand(Player player, GameRoom room) {
        if (player == null || isFrozen()) {
            return;
        }
        List<Card> deck = (deckType == DeckType.CHANCE) ? CHANCE_DECK : COMMUNITY_CHEST_DECK;
        Card drawn = deck.get(RANDOM.nextInt(deck.size()));
        drawn.apply(player, room);
    }
}
