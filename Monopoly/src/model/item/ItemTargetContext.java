package model.item;

/**
 * Ngữ cảnh mục tiêu khi người chơi kích hoạt một vật phẩm.
 * Tham khảo từ docs/event-and-item.md:
 * Chứa thông tin về người chơi mục tiêu, ô đất mục tiêu, hoặc số bước mong muốn.
 */
public class ItemTargetContext {
    private String targetPlayerId;
    private Integer targetTileIndex;
    private Integer desiredDiceValue;

    public ItemTargetContext() {
    }

    public ItemTargetContext(String targetPlayerId, Integer targetTileIndex, Integer desiredDiceValue) {
        this.targetPlayerId = targetPlayerId;
        this.targetTileIndex = targetTileIndex;
        this.desiredDiceValue = desiredDiceValue;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public Integer getTargetTileIndex() {
        return targetTileIndex;
    }

    public void setTargetTileIndex(Integer targetTileIndex) {
        this.targetTileIndex = targetTileIndex;
    }

    public Integer getDesiredDiceValue() {
        return desiredDiceValue;
    }

    public void setDesiredDiceValue(Integer desiredDiceValue) {
        this.desiredDiceValue = desiredDiceValue;
    }
}

