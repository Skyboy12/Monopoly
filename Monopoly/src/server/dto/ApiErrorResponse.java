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
                escape(code),
                escape(message),
                roomId == null ? "null" : ("\"" + escape(roomId) + "\""),
                turnId,
                timestamp.toString()
        );
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}