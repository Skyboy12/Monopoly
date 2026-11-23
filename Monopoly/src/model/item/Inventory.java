package model.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
        if (item == null || items.size() >= maxCapacity) {
            return false;
        }
        items.add(item);
        return true;
    }

    /**
     * Tiêu hao và xóa vật phẩm khỏi túi đồ khi sử dụng xong.
     *
     * @param itemId Mã định danh của vật phẩm cần xóa
     * @return Vật phẩm đã bị xóa hoặc null nếu không tìm thấy
     */
    public synchronized Item removeItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (itemId.equals(item.getId())) {
                iterator.remove();
                return item;
            }
        }
        return null;
    }

    /**
     * Kiểm tra xem người chơi có đang sở hữu vật phẩm này không.
     *
     * @param itemId Mã định danh của vật phẩm
     * @return true nếu có trong túi, false nếu không
     */
    public synchronized boolean hasItem(String itemId) {
        return getItemById(itemId) != null;
    }

    /**
     * Lấy thông tin vật phẩm trong túi đồ theo ID.
     *
     * @param itemId Mã định danh của vật phẩm
     * @return Item nếu tìm thấy, null nếu không có
     */
    public synchronized Item getItemById(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (Item item : items) {
            if (itemId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    /**
     * Lấy toàn bộ danh sách vật phẩm hiện có trong túi đồ.
     */
    public synchronized List<Item> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }
}
