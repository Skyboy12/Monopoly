package model.item;

/**
 * Giai đoạn mà vật phẩm có thể được kích hoạt (Trigger Phase).
 * Tham khảo từ docs/event-and-item.md:
 * - PRE_ROLL: Trước khi gieo xúc xắc (VD: MagicDice - chọn số bước).
 * - ON_LAND: Khi vừa dừng chân tại ô đất đối thủ (VD: RentShield - miễn tiền thuê).
 * - AWAITING_DECISION: Trong pha tự do đưa ra quyết định (VD: Dynamite, FortuneSwap, ArrestWarrant).
 */
public enum ItemTriggerPhase {
    PRE_ROLL,
    ON_LAND,
    AWAITING_DECISION
}

