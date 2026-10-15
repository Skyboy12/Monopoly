package model.event;

import model.GameRoom;

/**
 * Lớp trừu tượng cho Sự kiện đặc biệt (Special Events) trong Party Mode.
 * Áp dụng tính chất Abstraction & Polymorphism:
 * Ván đấu kích hoạt sự kiện thông qua phương thức onTrigger() và dọn dẹp qua onExpire().
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI CÁC LỚP CON (Tham khảo docs/event-and-item.md Mục 3.1):
 * ════════════════════════════════════════════════════════════════════════════════
 * 1. Sự kiện Toàn cục (Global Periodic Events):
 *    - RichTaxEvent (Thuế Nhà Giàu Siêu Cấp):
 *      Người chơi có số tiền mặt cao nhất bị trừ 40% tiền mặt chia đều cho các người chơi còn lại.
 *    - PropertyBoomEvent (Cơn Điên Bất Động Sản):
 *      Nhân 3 lần tiền thuê của toàn bộ các ô đất trong phòng cờ trong suốt durationTurns.
 *    - ContinentalDriftEvent (Đảo Lộn Đất Đai):
 *      Tráo đổi quyền sở hữu của 2 ô đất ngẫu nhiên giữa 2 người chơi khác nhau.
 *    - StimulusDayEvent (Ngày Phát Tiền Cứu Trợ):
 *      Ngân hàng tự động phát cho mỗi người chơi trong phòng $500.
 *
 * 2. Sự kiện Cục bộ (Tile & Environmental Events):
 *    - EarthquakeEvent (Động Đất Cục Bộ):
 *      Một nhóm màu hoặc khu phố ngẫu nhiên bị chấn động, mỗi ô bị sập 1 cấp nhà.
 *    - FrozenTileEvent (Mưa Bão Đóng Băng):
 *      Một số ô đất liên tiếp bị đóng băng, người chơi đáp vào không mất tiền thuê nhưng trượt thêm 2 bước.
 */
public abstract class GameEvent {
    private final String id;
    private final String name;
    private final String description;
    private final int durationTurns;

    public GameEvent(String id, String name, String description, int durationTurns) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.durationTurns = durationTurns;
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

    public int getDurationTurns() {
        return durationTurns;
    }

    /**
     * Kích hoạt hiệu ứng của sự kiện lên toàn bộ phòng cờ.
     *
     * @param room Phòng cờ hiện tại
     */
    public abstract void onTrigger(GameRoom room);

    /**
     * Hành vi dọn dẹp hoặc khôi phục trạng thái ban đầu khi sự kiện hết thời hạn (durationTurns về 0).
     *
     * @param room Phòng cờ hiện tại
     */
    public abstract void onExpire(GameRoom room);
}
