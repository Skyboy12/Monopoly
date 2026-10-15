package engine;

/**
 * Các giai đoạn trong một lượt chơi của phòng cờ.
 */
public enum TurnPhase {
    AWAITING_ROLL,
    RESOLVING_ACTION,
    AWAITING_DECISION,
    FINISHED
}
