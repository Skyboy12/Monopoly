package model.item;

import model.GameRoom;
import model.Player;

/**
 * Lớp trừu tượng đại diện cho một Vật Phẩm (Item) trong hệ thống Party Mode.
 * Áp dụng tính chất Abstraction & Polymorphism:
 * Game engine chỉ cần tương tác qua canUse() và applyEffect() mà không cần biết
 * chi tiết vật phẩm cụ thể là gì.
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI CÁC LỚP CON (Tham khảo docs/event-and-item.md Mục 2.2):
 * ════════════════════════════════════════════════════════════════════════════════
 * 1. MagicDiceItem (Xúc xắc Thần kỳ):
 *    - triggerPhase: PRE_ROLL
 *    - canUse: Kiểm tra desiredDiceValue trong context nằm trong khoảng [2, 12].
 *    - applyEffect: Gán cứng giá trị xúc xắc của lượt này thay vì gieo ngẫu nhiên.
 *
 * 2. RentShieldItem (Khiên Hộ mệnh):
 *    - triggerPhase: ON_LAND
 *    - canUse: Người chơi vừa đáp chân vào ô đất thuộc sở hữu của đối thủ.
 *    - applyEffect: Miễn 100% tiền thuê cho lần dừng chân này.
 *
 * 3. HostileTakeoverItem (Cưỡng chế Thu mua):
 *    - triggerPhase: AWAITING_DECISION
 *    - canUse: targetTileIndex thuộc sở hữu của người khác và người dùng đủ tiền niêm yết.
 *    - applyEffect: Trừ tiền người dùng trả cho chủ cũ và đổi chủ sở hữu ô đất.
 *
 * 4. DynamiteItem (Thùng Thuốc nổ):
 *    - triggerPhase: AWAITING_DECISION
 *    - canUse: targetTileIndex là StreetProperty có nhà hoặc khách sạn.
 *    - applyEffect: Phá hủy toàn bộ nhà/khách sạn trên ô đất chỉ định về lại đất trống.
 *
 * 5. ArrestWarrantItem (Lệnh Bắt Khẩn cấp):
 *    - triggerPhase: AWAITING_DECISION
 *    - canUse: targetPlayerId khác người dùng và đang ở trạng thái ACTIVE.
 *    - applyEffect: Đưa thẳng targetPlayer vào tù (vị trí 10, status = IN_JAIL).
 *
 * 6. FortuneSwapItem (Gậy Đổi Vận):
 *    - triggerPhase: AWAITING_DECISION
 *    - canUse: Có người chơi khác có tổng tài sản lớn hơn người dùng.
 *    - applyEffect: Hoán đổi vị trí trên bàn cờ với người chơi có tài sản cao nhất.
 */
public abstract class Item {
    private final String id;
    private final String name;
    private final String description;
    private final ItemTriggerPhase triggerPhase;

    public Item(String id, String name, String description, ItemTriggerPhase triggerPhase) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.triggerPhase = triggerPhase;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ItemTriggerPhase getTriggerPhase() {
        return triggerPhase;
    }

    /**
     * Kiểm tra điều kiện tiên quyết trước khi sử dụng vật phẩm.
     *
     * @param user    Người chơi muốn dùng vật phẩm
     * @param room    Phòng chơi hiện tại
     * @param context Ngữ cảnh mục tiêu (người chơi đích, ô đất đích, giá trị xúc xắc...)
     * @return true nếu thỏa mãn điều kiện sử dụng, ngược lại false
     */
    public abstract boolean canUse(Player user, GameRoom room, ItemTargetContext context);

    /**
     * Kích hoạt hiệu ứng chính của vật phẩm lên ván đấu (Server Authoritative).
     *
     * @param user    Người chơi sử dụng vật phẩm
     * @param room    Phòng chơi hiện tại
     * @param context Ngữ cảnh mục tiêu
     * @return true nếu hiệu ứng được thi hành thành công
     */
    public abstract boolean applyEffect(Player user, GameRoom room, ItemTargetContext context);
}
