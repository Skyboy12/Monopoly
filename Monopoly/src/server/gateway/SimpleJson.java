package server.gateway;

import network.GameMessage;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ phân tích và tuần tự hóa JSON thuần JDK (UTF-8, escape, số, boolean, mảng, object lồng nhau).
 */
public final class SimpleJson {
    private static final int MAX_DEPTH = 256;

    private SimpleJson() {
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Chuỗi JSON không được rỗng.");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Dữ liệu thừa sau JSON hợp lệ tại vị trí " + parser.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object parsed = parse(json);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("JSON không phải là một object.");
        }
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object parsed = parse(json);
        if (!(parsed instanceof List<?>)) {
            throw new IllegalArgumentException("JSON không phải là một array.");
        }
        return (List<Object>) parsed;
    }

    public static String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        return (val instanceof String s) ? s : null;
    }

    public static Integer getInteger(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Integer parsed = getInteger(map, key);
        return parsed != null ? parsed : defaultValue;
    }

    public static Boolean getBoolean(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(s.trim())) return Boolean.FALSE;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        return (val instanceof Map<?, ?>) ? (Map<String, Object>) val : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getArray(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        return (val instanceof List<?>) ? (List<Object>) val : null;
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
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

    public static String quote(String value) {
        return value == null ? "null" : ("\"" + escape(value) + "\"");
    }

    public static String toJson(GameMessage msg) {
        if (msg == null) {
            return "null";
        }
        return "{"
                + "\"type\":" + quote(msg.getType()) + ","
                + "\"senderId\":" + quote(msg.getSenderId()) + ","
                + "\"roomId\":" + quote(msg.getRoomId()) + ","
                + "\"turnId\":" + msg.getTurnId() + ","
                + "\"payload\":" + quote(msg.getPayload())
                + "}";
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value);
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof GameMessage msg) {
            sb.append(toJson(msg));
        } else if (value instanceof String s) {
            sb.append(quote(s));
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(n);
            }
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(String.valueOf(entry.getKey())));
                sb.append(':');
                writeJson(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                first = false;
                writeJson(sb, item);
            }
            sb.append(']');
        } else if (value.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                writeJson(sb, Array.get(value, i));
            }
            sb.append(']');
        } else {
            sb.append(quote(String.valueOf(value)));
        }
    }

    private static final class Parser {
        private final String input;
        private final int length;
        private int pos;

        Parser(String input) {
            this.input = input;
            this.length = input.length();
            this.pos = 0;
        }

        boolean isAtEnd() {
            return pos >= length;
        }

        void skipWhitespace() {
            while (pos < length) {
                char c = input.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("JSON lồng nhau quá sâu.");
            }
            skipWhitespace();
            if (pos >= length) {
                throw new IllegalArgumentException("Kết thúc JSON bất ngờ tại vị trí " + pos);
            }
            char c = input.charAt(pos);
            return switch (c) {
                case '{' -> parseObjectValue(depth + 1);
                case '[' -> parseArrayValue(depth + 1);
                case '"' -> parseStringValue();
                case 't', 'f' -> parseBooleanValue();
                case 'n' -> parseNullValue();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumberValue();
                    }
                    throw new IllegalArgumentException("Ký tự JSON không hợp lệ '" + c + "' tại vị trí " + pos);
                }
            };
        }

        private Map<String, Object> parseObjectValue(int depth) {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new IllegalArgumentException("Mong đợi key dạng chuỗi tại vị trí " + pos);
                }
                String key = parseStringValue();
                skipWhitespace();
                expect(':');
                Object val = parseValue(depth);
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Mong đợi ',' hoặc '}' tại vị trí " + pos);
                }
            }
        }

        private List<Object> parseArrayValue(int depth) {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue(depth));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Mong đợi ',' hoặc ']' tại vị trí " + pos);
                }
            }
        }

        private String parseStringValue() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new IllegalArgumentException("Ký tự điều khiển chưa escape trong chuỗi JSON.");
                }
                if (c == '\\') {
                    if (pos >= length) {
                        throw new IllegalArgumentException("Chuỗi JSON escape chưa hoàn chỉnh.");
                    }
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > length) {
                                throw new IllegalArgumentException("Chuỗi \\uXXXX không hợp lệ.");
                            }
                            String hex = input.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Mã hex unicode không hợp lệ: " + hex, e);
                            }
                        }
                        default -> throw new IllegalArgumentException("Ký tự escape không hợp lệ: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Chuỗi JSON chưa đóng dấu ngoặc kép.");
        }

        private Number parseNumberValue() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            if (pos >= length || !isDigit(input.charAt(pos))) {
                throw new IllegalArgumentException("Số JSON không hợp lệ tại vị trí " + start);
            }
            if (input.charAt(pos) == '0') {
                pos++;
                if (pos < length && isDigit(input.charAt(pos))) {
                    throw new IllegalArgumentException("Số JSON không được có số 0 đứng đầu tại vị trí " + start);
                }
            } else {
                while (pos < length && isDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            boolean isFloat = false;
            if (pos < length && input.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                if (pos >= length || !isDigit(input.charAt(pos))) {
                    throw new IllegalArgumentException("Phần thập phân không hợp lệ tại vị trí " + start);
                }
                while (pos < length && isDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            if (pos < length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos >= length || !isDigit(input.charAt(pos))) {
                    throw new IllegalArgumentException("Phần mũ số học không hợp lệ tại vị trí " + start);
                }
                while (pos < length && isDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            String raw = input.substring(start, pos);
            try {
                if (isFloat) {
                    return Double.parseDouble(raw);
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                return Double.parseDouble(raw);
            }
        }

        private Boolean parseBooleanValue() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Giá trị boolean không hợp lệ tại vị trí " + pos);
        }

        private Object parseNullValue() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Giá trị null không hợp lệ tại vị trí " + pos);
        }

        private void expect(char expected) {
            if (pos >= length || input.charAt(pos) != expected) {
                throw new IllegalArgumentException("Mong đợi '" + expected + "' tại vị trí " + pos);
            }
            pos++;
        }

        private boolean peek(char ch) {
            return pos < length && input.charAt(pos) == ch;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
