package engine.bot;

import engine.GameEngine;
import engine.TurnPhase;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.tile.PropertyTile;
import model.tile.StreetProperty;
import model.tile.Tile;

/**
 * Chiến lược mặc định cho Bot và người chơi bị timeout.
 * Không giữ lock trên {@code room} khi gọi các phương thức {@code engine.*},
 * tránh deadlock ABBA giữa {@code room} và {@code engine}.
 */
public class DefaultBotStrategy implements BotStrategy {

    @Override
    public void playTurn(GameEngine engine, Player initialPlayer) {
        if (engine == null || initialPlayer == null) {
            return;
        }

        GameRoom room = engine.getRoom();
        final String roomId;
        final String playerId = initialPlayer.getId();
        final int turnId;
        synchronized (room) {
            if (room.getStatus() != RoomStatus.PLAYING) {
                return;
            }
            roomId = room.getId();
            turnId = room.getTurnId();
        }

        try {
            while (engine.isBotTurnActive(roomId, playerId, turnId)) {
                Player player = room.getCurrentPlayer();
                if (player == null || !player.getId().equals(playerId)) {
                    return;
                }

                TurnPhase phase = engine.getBotActionPhase(roomId, playerId, turnId);
                if (phase == null) {
                    return;
                }

                if (phase == TurnPhase.AWAITING_ROLL) {
                    if (player.getStatus() == PlayerStatus.IN_JAIL) {
                        if (player.getMoney() >= 3L * Player.BAIL_AMOUNT) {
                            if (!engine.payBail(playerId, turnId)) {
                                engine.endTurn(playerId, turnId);
                                return;
                            }
                            continue;
                        } else {
                            if (!engine.rollDice(playerId, turnId)) {
                                engine.endTurn(playerId, turnId);
                                return;
                            }
                            continue;
                        }
                    }

                    if (!engine.rollDice(playerId, turnId)) {
                        engine.endTurn(playerId, turnId);
                        return;
                    }
                    continue;
                }

                if (phase == TurnPhase.AWAITING_DECISION) {
                    Tile currentTile = room.getBoard().getTile(player.getPosition());
                    if (currentTile instanceof PropertyTile) {
                        PropertyTile property = (PropertyTile) currentTile;
                        if (!property.hasOwner() && player.getMoney() >= 3L * property.getPrice()) {
                            engine.buyProperty(playerId, turnId);
                        }
                    }

                    for (PropertyTile owned : player.getOwnedProperties()) {
                        if (owned instanceof StreetProperty) {
                            StreetProperty street = (StreetProperty) owned;
                            if (street.canUpgrade() && player.getMoney() >= 4L * street.getBuildingCost()) {
                                if (engine.upgradeProperty(playerId, turnId, street.getIndex())) {
                                    break;
                                }
                            }
                        }
                    }

                    if (!engine.endTurn(playerId, turnId)) {
                        return;
                    }

                    if (room.getTurnId() == turnId
                            && room.getCurrentPlayer() != null
                            && room.getCurrentPlayer().getId().equals(playerId)
                            && engine.getBotActionPhase(roomId, playerId, turnId) == TurnPhase.AWAITING_ROLL) {
                        continue;
                    }
                    return;
                }

                engine.endTurn(playerId, turnId);
                return;
            }
        } catch (RuntimeException e) {
            engine.endTurn(playerId, turnId);
        }
    }
}
