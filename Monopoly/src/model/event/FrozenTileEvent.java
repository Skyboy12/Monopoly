package model.event;

import model.GameRoom;
import model.tile.Tile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Sự kiện Cục bộ - Mưa Bão Đóng Băng:
 * Đóng băng 3 ô cờ liên tiếp, không mất tiền thuê nhưng trượt thêm 2 bước.
 */
public class FrozenTileEvent extends GameEvent {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int FROZEN_COUNT = 3;
    private final List<Integer> frozenTileIndices = new ArrayList<>();

    public FrozenTileEvent() {
        super(
                "FROZEN_TILE",
                "Mưa Bão Đóng Băng",
                "Đóng băng 3 ô cờ liên tiếp trong 3 lượt; người chơi đáp vào không mất tiền thuê nhưng trượt thêm 2 bước.",
                3
        );
    }

    @Override
    public void onTrigger(GameRoom room) {
        if (room == null || room.getBoard() == null) {
            return;
        }
        List<Tile> tiles = room.getBoard().getTiles();
        if (tiles.isEmpty()) {
            return;
        }
        frozenTileIndices.clear();
        int boardSize = tiles.size();
        int startIndex = RANDOM.nextInt(boardSize);
        for (int i = 0; i < Math.min(FROZEN_COUNT, boardSize); i++) {
            int idx = (startIndex + i) % boardSize;
            tiles.get(idx).setFrozen(true);
            frozenTileIndices.add(idx);
        }
    }

    @Override
    public void onExpire(GameRoom room) {
        if (room == null || room.getBoard() == null) {
            return;
        }
        for (int idx : frozenTileIndices) {
            boolean heldByOther = room.getEventManager() != null
                    && room.getEventManager().isTileFrozenByOtherEvent(this, idx);
            if (!heldByOther) {
                Tile tile = room.getBoard().getTile(idx);
                if (tile != null) {
                    tile.setFrozen(false);
                }
            }
        }
        frozenTileIndices.clear();
    }

    public List<Integer> getFrozenTileIndices() {
        return new ArrayList<>(frozenTileIndices);
    }
}
