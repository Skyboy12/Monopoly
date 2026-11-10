package engine.bot;

import engine.GameEngine;
import engine.TurnPhase;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.tile.PropertyTile;
import model.tile.Tile;

/**
 * Điều khiển bot hoặc thay người chơi xử lý lượt khi bị timeout.
 * Bot mua tài sản khi số dư tối thiểu bằng 3 lần giá mua.
 */
public class BotController {

    public static void processBotTurn(GameEngine engine) {
        if (engine == null) {
            return;
        }

        /*
         * Khóa trên chính GameEngine để toàn bộ lượt bot không chen ngang
         * với lệnh người chơi gửi đến cùng engine.
         * Các method synchronized của engine có thể được gọi lại an toàn
         * trong cùng thread vì synchronized của Java là reentrant.
         */
        synchronized (engine) {
            GameRoom room = engine.getRoom();
            Player player = room.getCurrentPlayer();

            if (player == null || room.getStatus() != RoomStatus.PLAYING) {
                return;
            }

            String playerId = player.getId();
            int turnId = room.getTurnId();

            // Có thể xử lý cả timeout xảy ra trước khi gieo hoặc sau khi đã gieo.
            while (room.getStatus() == RoomStatus.PLAYING
                    && room.getTurnId() == turnId
                    && room.getCurrentPlayer() != null
                    && room.getCurrentPlayer().getId().equals(playerId)) {

                TurnPhase phase = engine.getCurrentPhase();

                if (phase == TurnPhase.AWAITING_ROLL) {
                    Player currentPlayer = room.getCurrentPlayer();

                    // Nếu đang ở tù, bot tự trả $50 nếu đủ tiền; nếu không thì bỏ lượt.
                    if (currentPlayer.getStatus() == PlayerStatus.IN_JAIL) {
                        if (currentPlayer.getMoney() >= 50) {
                            if (!engine.payBail(playerId, turnId)) {
                                return;
                            }
                        } else {
                            engine.endTurn(playerId, turnId);
                            return;
                        }
                    }

                    if (!engine.rollDice(playerId, turnId)) {
                        return;
                    }
                } else if (phase == TurnPhase.AWAITING_DECISION) {
                    buyIfWorthwhile(engine, room, playerId, turnId);

                    if (!engine.endTurn(playerId, turnId)) {
                        return;
                    }
                } else {
                    // RESOLVING_ACTION và FINISHED không phải phase để bot ra lệnh.
                    return;
                }
            }
        }
    }

    /** Bot mua đất khi tiền hiện có >= 3 lần giá mua. */
    private static void buyIfWorthwhile(GameEngine engine, GameRoom room, String playerId,int turnId) {
        Player player = room.getCurrentPlayer();
        if (player == null || !player.getId().equals(playerId)) {
            return;
        }

        Tile tile = room.getBoard().getTile(player.getPosition());
        if (!(tile instanceof PropertyTile)) {
            return;
        }

        PropertyTile property = (PropertyTile) tile;
        long requiredMoney = 3L * property.getPrice();

        if (!property.hasOwner() && player.getMoney() >= requiredMoney) {
            engine.buyProperty(playerId, turnId);
        }
    }
}
