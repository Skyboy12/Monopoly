package model;

import model.tile.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Bàn cờ Monopoly chứa 40 ô cờ tiêu chuẩn.
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Khởi tạo danh sách 40 ô theo đúng thứ tự (index từ 0 đến 39).
 * - Sử dụng các lớp kế thừa chuyên biệt: GoTile, StreetProperty, RailroadProperty,
 *   UtilityProperty, TaxTile, JailTile, GoToJailTile, FreeParkingTile.
 */
public class Board {
    private final List<Tile> tiles;

    public Board() {
        this.tiles = new ArrayList<>(40);
        initializeBoard();
    }

    private void initializeBoard() {
        // TODO: Khởi tạo và thêm 40 ô cờ vào danh sách tiles theo đúng vị trí từ 0 đến 39
    }

    public Tile getTile(int index) {
        // TODO: Lấy ô cờ theo vị trí (chuẩn hóa mod 40 để tránh lỗi văng index)
        return null;
    }

    public List<Tile> getTiles() {
        // TODO: Trả về danh sách toàn bộ các ô cờ trên bàn
        return null;
    }
}
