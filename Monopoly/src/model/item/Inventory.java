package model.item;

import java.util.ArrayList;
import java.util.List;

/**
 * Túi đồ của người chơi, đóng gói quản lý danh sách vật phẩm (Encapsulation).
 * <p>
 * ════════════════════════════════════════════════════════════════════════════════
 * HƯỚNG DẪN TRIỂN KHAI (Tham khảo docs/event-and-item.md Mục 2.3 & 2.4):
 * ════════════════════════════════════════════════════════════════════════════════
 * - Túi đồ có sức chứa giới hạn (mặc định 3 - 5 ô chứa).
 * - addItem(): Chỉ cho phép thêm nếu items.size() < maxCapacity.
 * - removeItem(): Tiêu hao vật phẩm sau khi sử dụng thành công qua applyEffect().
 * - hasItem(): Kiểm tra người chơi có đang sở hữu vật phẩm theo ID trước khi kích hoạt.
 * - getItems(): Trả về danh sách bất biến (Unmodifiable List) để chống sửa mảng từ bên ngoài.
 */
public class Inventory {
    private final int maxCapacity;
    private final List<Item> items;

    public Inventory(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.items = new ArrayList<>();
    }

    /**
     * Thêm một vật phẩm mới vào túi đồ (ví dụ: nhặt được từ rương, qua ô GO, cứu trợ).
     *
     * @param item Vật phẩm muốn thêm
     * @return true nếu thêm thành công, false nếu túi đồ đã đầy
     */
    public synchronized boolean addItem(Item item) {
        // TODO: Kiểm tra nếu items.size() < maxCapacity thì thêm item vào list và trả về true; ngược lại false
        return false;
    }

    /**
     * Tiêu hao và xóa vật phẩm khỏi túi đồ khi sử dụng xong.
     *
     * @param itemId Mã định danh của vật phẩm cần xóa
     * @return Vật phẩm đã bị xóa hoặc null nếu không tìm thấy
     */
    public synchronized Item removeItem(String itemId) {
        // TODO: Tìm item có id khớp với itemId, xóa khỏi list và trả về item đó; nếu không có trả về null
        return null;
    }

    /**
     * Kiểm tra xem người chơi có đang sở hữu vật phẩm này không.
     *
     * @param itemId Mã định danh của vật phẩm
     * @return true nếu có trong túi, false nếu không
     */
    public synchronized boolean hasItem(String itemId) {
        // TODO: Duyệt danh sách items kiểm tra xem có item nào có id khớp với itemId không
        return false;
    }

    /**
     * Lấy thông tin vật phẩm trong túi đồ theo ID.
     *
     * @param itemId Mã định danh của vật phẩm
     * @return Item nếu tìm thấy, null nếu không có
     */
    public synchronized Item getItemById(String itemId) {
        // TODO: Tìm và trả về item theo ID mà không xóa khỏi túi đồ
        return null;
    }

    /**
     * Lấy toàn bộ danh sách vật phẩm hiện có trong túi đồ.
     */
    public synchronized List<Item> getItems() {
        // TODO: Trả về bản sao hoặc Collections.unmodifiableList(items)
        return null;
    }

    public int getMaxCapacity() {
        // TODO: Trả về sức chứa tối đa của túi đồ
        return 0;
    }
}
