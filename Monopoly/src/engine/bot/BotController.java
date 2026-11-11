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
 * Controller tự động cho người chơi bot hoặc khi người chơi timeout.
 *
 * Bot mua tài sản khi số dư >= 3 lần giá mua.
 */
public class BotController {

    public static void processBotTurn(GameEngine engine) {

        // Kiểm tra engine
        if (engine == null) {
            return;
        }

        // 1. Lấy người chơi hiện tại
        GameRoom room = engine.getRoom();
        Player player = room.getCurrentPlayer();

        if (player == null || room.getStatus() != RoomStatus.PLAYING) {
            return;
        }

        String playerId = player.getId();
        int turnId = room.getTurnId();

        // 2. Xử lý lượt bot
        while (engine.isBotTurnActive(
                room.getId(), playerId, turnId)) {

            player = room.getCurrentPlayer();

            if (player == null || !player.getId().equals(playerId)) {
                return;
            }

            TurnPhase phase = engine.getBotActionPhase(
                    room.getId(), playerId, turnId
            );

            // Nếu đang ở tù thì bỏ lượt, không xử lý tiền chuộc.
            if (phase == TurnPhase.AWAITING_ROLL
                    && player.getStatus() == PlayerStatus.IN_JAIL) {

                engine.endTurn(playerId, turnId);
                return;
            }

            // 3. Gieo xúc xắc
            if (phase == TurnPhase.AWAITING_ROLL) {

                if (!engine.rollDice(playerId, turnId)) {
                    return;
                }

                continue;
            }

            // 4. Quyết định mua đất
            if (phase == TurnPhase.AWAITING_DECISION) {

                buyIfWorthwhile(engine, room, playerId, turnId);

                // 5. Kết thúc lượt
                if (!engine.endTurn(playerId, turnId)) {
                    return;
                }

                // Nếu gieo đôi thì tiếp tục lượt hiện tại.
                if (room.getTurnId() == turnId
                        && room.getCurrentPlayer() != null
                        && room.getCurrentPlayer().getId().equals(playerId)
                        && engine.getBotActionPhase(
                                room.getId(), playerId, turnId
                        ) == TurnPhase.AWAITING_ROLL) {

                    continue;
                }

                return;
            }

            // Không xử lý khi phase không hợp lệ.
            return;
        }
    }

    /**
     * Bot mua đất nếu đủ tiền.
     */
    private static void buyIfWorthwhile(
            GameEngine engine,
            GameRoom room,
            String playerId,
            int turnId) {

        synchronized (room) {

            Player player = room.getCurrentPlayer();

            if (player == null || !player.getId().equals(playerId)
                    || room.getTurnId() != turnId) {
                return;
            }

            Tile tile = room.getBoard().getTile(player.getPosition());

            if (!(tile instanceof PropertyTile)) {
                return;
            }

            PropertyTile property = (PropertyTile) tile;

            long requiredMoney = 3L * property.getPrice();

            if (!property.hasOwner()
                    && player.getMoney() >= requiredMoney) {

                engine.buyProperty(playerId, turnId);
            }
        }
    }
}
