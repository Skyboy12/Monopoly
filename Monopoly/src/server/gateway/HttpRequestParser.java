package server.gateway;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bộ phân tích HTTP/1.1 thuần JDK đọc trên luồng byte thô (an toàn cho body UTF-8 và nâng cấp WebSocket).
 */
public final class HttpRequestParser {
    public static final int MAX_BODY_BYTES = 102400; // 100 KB
    private static final int MAX_HEADER_BYTES = 32768;
    private static final int MAX_LINE_BYTES = 8192;
    private static final int MAX_HEADERS = 100;

    private HttpRequestParser() {
    }

    public static final class HttpRequest {
        private final String method;
        private final String rawTarget;
        private final String path;
        private final String rawQuery;
        private final Map<String, String> queryParams;
        private final Map<String, String> headers;
        private final byte[] body;
        private final String version;

        public HttpRequest(String method, String rawTarget, String path, String rawQuery,
                           Map<String, String> queryParams, Map<String, String> headers,
                           byte[] body, String version) {
            this.method = method;
            this.rawTarget = rawTarget;
            this.path = path;
            this.rawQuery = rawQuery;
            this.queryParams = Collections.unmodifiableMap(queryParams);
            this.headers = Collections.unmodifiableMap(headers);
            this.body = body;
            this.version = version;
        }

        public String getMethod() {
            return method;
        }

        public String getRawTarget() {
            return rawTarget;
        }

        public String getPath() {
            return path;
        }

        public String getRawQuery() {
            return rawQuery;
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public String getHeader(String name) {
            return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
        }

        public byte[] getBodyBytes() {
            return body;
        }

        public String getBodyString() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public String getVersion() {
            return version;
        }

        public boolean isKeepAlive() {
            String connection = getHeader("connection");
            if (connection != null) {
                String lower = connection.toLowerCase(Locale.ROOT);
                if (lower.contains("close")) {
                    return false;
                }
                if (lower.contains("keep-alive")) {
                    return true;
                }
            }
            return !"HTTP/1.0".equalsIgnoreCase(version);
        }
    }

    public static final class HttpParseException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int statusCode;
        private final String errorCode;

        public HttpParseException(int statusCode, String errorCode, String message) {
            super(message);
            this.statusCode = statusCode;
            this.errorCode = errorCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    public static HttpRequest parse(InputStream in) throws IOException {
        LineResult requestLineRes = readHeaderLine(in, true);
        if (requestLineRes == null) {
            return null; // Client đóng kết nối sạch giữa các request
        }

        int totalHeaderBytes = requestLineRes.bytesRead;
        String requestLine = requestLineRes.line;
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new HttpParseException(400, "BAD_REQUEST", "Request line HTTP không hợp lệ.");
        }

        String method = parts[0].trim().toUpperCase(Locale.ROOT);
        String rawTarget = parts[1].trim();
        String version = parts[2].trim().toUpperCase(Locale.ROOT);
        if (method.isEmpty() || rawTarget.isEmpty() || !version.startsWith("HTTP/1.")) {
            throw new HttpParseException(400, "BAD_REQUEST", "Request line HTTP không hợp lệ.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        int headerCount = 0;
        while (true) {
            LineResult headerLine = readHeaderLine(in, false);
            if (headerLine == null) {
                throw new HttpParseException(400, "BAD_REQUEST", "Header HTTP chưa kết thúc.");
            }
            totalHeaderBytes += headerLine.bytesRead;
            if (totalHeaderBytes > MAX_HEADER_BYTES) {
                throw new HttpParseException(400, "HEADER_TOO_LARGE", "Kích thước HTTP headers vượt quá giới hạn.");
            }
            if (headerLine.line.isEmpty()) {
                break;
            }
            if (++headerCount > MAX_HEADERS) {
                throw new HttpParseException(400, "HEADER_TOO_LARGE", "Số lượng HTTP headers vượt quá giới hạn.");
            }
            int colon = headerLine.line.indexOf(':');
            if (colon <= 0) {
                throw new HttpParseException(400, "BAD_REQUEST", "Định dạng HTTP header không hợp lệ.");
            }
            String key = headerLine.line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = headerLine.line.substring(colon + 1).trim();
            headers.merge(key, value, (oldVal, newVal) -> oldVal + ", " + newVal);
        }

        String targetWithoutFragment = stripFragment(rawTarget);
        String normalizedTarget = normalizeTargetPath(targetWithoutFragment);
        int qIdx = normalizedTarget.indexOf('?');
        String path = (qIdx >= 0) ? normalizedTarget.substring(0, qIdx) : normalizedTarget;
        String rawQuery = (qIdx >= 0) ? normalizedTarget.substring(qIdx + 1) : "";
        Map<String, String> queryParams = parseQueryParams(rawQuery);

        byte[] body = readBody(in, headers);
        return new HttpRequest(method, rawTarget, path, rawQuery, queryParams, headers, body, version);
    }

    private static byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String transferEncoding = headers.get("transfer-encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return readChunkedBody(in);
        }

        String contentLengthHeader = headers.get("content-length");
        if (contentLengthHeader == null || contentLengthHeader.isBlank()) {
            return new byte[0];
        }

        long contentLength;
        try {
            contentLength = Long.parseLong(contentLengthHeader.trim());
        } catch (NumberFormatException e) {
            throw new HttpParseException(400, "INVALID_CONTENT_LENGTH", "Content-Length không hợp lệ.");
        }

        if (contentLength < 0) {
            throw new HttpParseException(400, "INVALID_CONTENT_LENGTH", "Content-Length không được âm.");
        }
        if (contentLength > MAX_BODY_BYTES) {
            throw new HttpParseException(413, "PAYLOAD_TOO_LARGE", "Kích thước payload vượt quá 100KB (102400 bytes).");
        }
        if (contentLength == 0) {
            return new byte[0];
        }

        int len = (int) contentLength;
        byte[] body = in.readNBytes(len);
        if (body.length < len) {
            throw new HttpParseException(400, "INCOMPLETE_BODY", "Nội dung request body bị thiếu dữ liệu.");
        }
        return body;
    }

    private static byte[] readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            LineResult sizeLine = readHeaderLine(in, false);
            if (sizeLine == null) {
                throw new HttpParseException(400, "INCOMPLETE_BODY", "Chunk header không đầy đủ.");
            }
            String hex = sizeLine.line;
            int semi = hex.indexOf(';');
            if (semi >= 0) {
                hex = hex.substring(0, semi);
            }
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(hex.trim(), 16);
            } catch (NumberFormatException e) {
                throw new HttpParseException(400, "BAD_REQUEST", "Kích thước chunk không hợp lệ.");
            }
            if (chunkSize < 0) {
                throw new HttpParseException(400, "BAD_REQUEST", "Kích thước chunk âm.");
            }
            if (chunkSize == 0) {
                // Đọc trailer tới dòng trống
                while (true) {
                    LineResult trailer = readHeaderLine(in, false);
                    if (trailer == null || trailer.line.isEmpty()) {
                        break;
                    }
                }
                return out.toByteArray();
            }
            if (out.size() + chunkSize > MAX_BODY_BYTES) {
                throw new HttpParseException(413, "PAYLOAD_TOO_LARGE", "Kích thước payload vượt quá 100KB (102400 bytes).");
            }
            byte[] chunk = in.readNBytes(chunkSize);
            if (chunk.length < chunkSize) {
                throw new HttpParseException(400, "INCOMPLETE_BODY", "Dữ liệu chunk bị cắt giữa chừng.");
            }
            out.write(chunk);
            LineResult crlf = readHeaderLine(in, false);
            if (crlf == null || !crlf.line.isEmpty()) {
                throw new HttpParseException(400, "BAD_REQUEST", "Thiếu CRLF sau chunk.");
            }
        }
    }

    private static String stripFragment(String target) {
        int hash = target.indexOf('#');
        return hash >= 0 ? target.substring(0, hash) : target;
    }

    private static String normalizeTargetPath(String target) {
        if ("*".equals(target)) {
            return "*";
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            try {
                URI uri = URI.create(target);
                String rawPath = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();
                return uri.getRawQuery() != null ? (rawPath + "?" + uri.getRawQuery()) : rawPath;
            } catch (IllegalArgumentException ignored) {
                return target;
            }
        }
        return target;
    }

    public static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String rawKey = (eq >= 0) ? pair.substring(0, eq) : pair;
            String rawVal = (eq >= 0) ? pair.substring(eq + 1) : "";
            String key = urlDecode(rawKey);
            String val = urlDecode(rawVal);
            params.put(key, val);
        }
        return params;
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static LineResult readHeaderLine(InputStream in, boolean allowCleanEof) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int bytesRead = 0;
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (allowCleanEof && bytesRead == 0) {
                    return null;
                }
                if (bytesRead == 0) {
                    return null;
                }
                throw new HttpParseException(400, "BAD_REQUEST", "Kết nối đóng đột ngột khi đang đọc HTTP header.");
            }
            bytesRead++;
            if (bytesRead > MAX_LINE_BYTES) {
                throw new HttpParseException(400, "HEADER_TOO_LARGE", "Dòng HTTP header quá dài.");
            }
            if (b == '\n') {
                byte[] raw = buffer.toByteArray();
                int len = raw.length;
                if (len > 0 && raw[len - 1] == '\r') {
                    len--;
                }
                return new LineResult(new String(raw, 0, len, StandardCharsets.ISO_8859_1), bytesRead);
            }
            buffer.write(b);
        }
    }

    private record LineResult(String line, int bytesRead) {
    }
}
