package server.dto;

import java.time.Instant;

/**
 * Định dạng phản hồi lỗi thống nhất cho toàn bộ REST API và WebSocket.
 */
public class ApiErrorResponse {
    private final String code;
    private final String message;
    private final String roomId;
    private final int turnId;
    private final Instant timestamp;

    public ApiErrorResponse(String code, String message, String roomId, int turnId) {
        this.code = code;
        this.message = message;
        this.roomId = roomId;
        this.turnId = turnId;
        this.timestamp = Instant.now();
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getRoomId() { return roomId; }
    public int getTurnId() { return turnId; }
    public Instant getTimestamp() { return timestamp; }

    public String toJson() {
        return String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"roomId\":%s,\"turnId\":%d,\"timestamp\":\"%s\"}",
                code,
                message,
                roomId == null ? "null" : ("\"" + roomId + "\""),
                turnId,
                timestamp.toString()
        );
    }
}