package network;

import java.io.Serializable;

/**
 * Message DTO chuẩn trao đổi giữa Client và Server (WebSocket / Socket).
 * <p>
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đóng gói các lệnh điều khiển từ Client (ROLL_DICE, BUY_PROPERTY, END_TURN)
 * - Truyền tải sự kiện từ Server (DICE_ROLLED, PROPERTY_BOUGHT, TURN_CHANGED)
 */
public class GameMessage implements Serializable {
    private String type;
    private String senderId;
    private String roomId;
    private int turnId;
    private String payload;

    public GameMessage() {
    }

    public GameMessage(String type, String senderId, String roomId, int turnId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.roomId = roomId;
        this.turnId = turnId;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public int getTurnId() {
        return turnId;
    }

    public void setTurnId(int turnId) {
        this.turnId = turnId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
