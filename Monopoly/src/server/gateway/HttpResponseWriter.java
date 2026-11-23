package server.gateway;

import network.GameMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Tiện ích ghi phản hồi HTTP/1.1 kèm CORS headers và ánh xạ mã lỗi REST chuẩn.
 */
public final class HttpResponseWriter {
    public static final String CORS_ALLOW_ORIGIN = "*";
    public static final String CORS_ALLOW_METHODS = "GET, POST, OPTIONS";
    public static final String CORS_ALLOW_HEADERS = "Content-Type, Authorization, X-Requested-With, X-Origin-Verify";

    private HttpResponseWriter() {
    }

    public static void writeNoContent(OutputStream out, boolean keepAlive) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 204 No Content\r\n");
        appendCorsHeaders(sb);
        sb.append("Content-Length: 0\r\n");
        sb.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    public static void writeJson(OutputStream out, int statusCode, String jsonBody, boolean keepAlive) throws IOException {
        writeJson(out, statusCode, jsonBody, keepAlive, null);
    }

    public static void writeJson(OutputStream out, int statusCode, String jsonBody,
                                 boolean keepAlive, Map<String, String> extraHeaders) throws IOException {
        byte[] bodyBytes = (jsonBody == null ? "" : jsonBody).getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(' ').append(reasonPhrase(statusCode)).append("\r\n");
        appendCorsHeaders(sb);
        sb.append("Content-Type: application/json; charset=utf-8\r\n");
        sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        sb.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");

        byte[] headerBytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
        if (bodyBytes.length == 0) {
            out.write(headerBytes);
        } else {
            byte[] combined = new byte[headerBytes.length + bodyBytes.length];
            System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
            System.arraycopy(bodyBytes, 0, combined, headerBytes.length, bodyBytes.length);
            out.write(combined);
        }
        out.flush();
    }

    public static void writeGameMessage(OutputStream out, int statusCode, GameMessage message, boolean keepAlive) throws IOException {
        writeJson(out, statusCode, SimpleJson.toJson(message), keepAlive);
    }

    public static void writeMethodNotAllowed(OutputStream out, boolean keepAlive) throws IOException {
        GameMessage error = new GameMessage(
                "ERROR",
                null,
                null,
                0,
                "{\"error\":\"METHOD_NOT_ALLOWED\",\"message\":\"Phương thức HTTP không được hỗ trợ.\"}"
        );
        writeJson(out, 405, SimpleJson.toJson(error), keepAlive, Map.of("Allow", CORS_ALLOW_METHODS));
    }

    public static void writeWebSocketUpgrade(OutputStream out, String acceptKey, String negotiatedProtocol) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 101 Switching Protocols\r\n");
        sb.append("Upgrade: websocket\r\n");
        sb.append("Connection: Upgrade\r\n");
        sb.append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n");
        if (negotiatedProtocol != null && !negotiatedProtocol.isBlank()) {
            sb.append("Sec-WebSocket-Protocol: ").append(negotiatedProtocol).append("\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    public static int mapErrorStatus(GameMessage errorMessage) {
        if (errorMessage == null || errorMessage.getPayload() == null) {
            return 400;
        }
        String code = null;
        try {
            Map<String, Object> payload = SimpleJson.parseObject(errorMessage.getPayload());
            code = SimpleJson.getString(payload, "error");
        } catch (IllegalArgumentException ignored) {
            return 400;
        }
        return mapErrorCode(code);
    }

    public static int mapErrorCode(String code) {
        if (code == null || code.isBlank()) {
            return 400;
        }
        if ("UNAUTHORIZED".equals(code) || "INVALID_CREDENTIALS".equals(code)) {
            return 401;
        }
        if (code.startsWith("FORBIDDEN_") || "FORBIDDEN".equals(code)) {
            return 403;
        }
        if (code.endsWith("_NOT_FOUND") || "NOT_FOUND".equals(code)) {
            return 404;
        }
        if ("USERNAME_TAKEN".equals(code)
                || "ALREADY_IN_ROOM".equals(code)
                || "ROOM_FULL".equals(code)
                || "ROOM_NOT_WAITING".equals(code)
                || "CONFLICT".equals(code)) {
            return 409;
        }
        if ("PAYLOAD_TOO_LARGE".equals(code)) {
            return 413;
        }
        if ("UNSUPPORTED_MEDIA_TYPE".equals(code)) {
            return 415;
        }
        if ("INTERNAL_SERVER_ERROR".equals(code)) {
            return 500;
        }
        return 400;
    }

    private static void appendCorsHeaders(StringBuilder sb) {
        sb.append("Access-Control-Allow-Origin: ").append(CORS_ALLOW_ORIGIN).append("\r\n");
        sb.append("Access-Control-Allow-Methods: ").append(CORS_ALLOW_METHODS).append("\r\n");
        sb.append("Access-Control-Allow-Headers: ").append(CORS_ALLOW_HEADERS).append("\r\n");
        sb.append("Access-Control-Max-Age: 86400\r\n");
    }

    private static String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 426 -> "Upgrade Required";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }
}
