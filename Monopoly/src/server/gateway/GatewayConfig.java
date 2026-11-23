package server.gateway;

import server.security.CloudflareOriginFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình khởi động cho API Gateway.
 * Thứ tự ưu tiên: CLI args > System environment variables > file .env > giá trị mặc định an toàn.
 */
public class GatewayConfig {
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_MODE = "dev";
    public static final String DEFAULT_JWT_SECRET = "MonopolyJwtSecretKey2026PtitOopProject";
    public static final long DEFAULT_JWT_VALIDITY_MS = 3600_000L;

    private final int port;
    private final String mode;
    private final String originSecret;
    private final String jwtSecret;
    private final long jwtValidityMs;

    public GatewayConfig(int port, String mode, String originSecret, String jwtSecret, long jwtValidityMs) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port phải nằm trong khoảng 0 - 65535: " + port);
        }
        String normalizedMode = normalizeMode(mode);
        // Xác thực mode thông qua CloudflareOriginFilter ngay khi khởi tạo config
        CloudflareOriginFilter.forEnvironment(normalizedMode, originSecret);

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret không được để trống.");
        }
        if (jwtValidityMs <= 0) {
            throw new IllegalArgumentException("JWT validity phải > 0.");
        }

        this.port = port;
        this.mode = normalizedMode;
        this.originSecret = originSecret;
        this.jwtSecret = jwtSecret;
        this.jwtValidityMs = jwtValidityMs;
    }

    public static GatewayConfig fromArgsAndEnv(String[] args) {
        Path envPath = findDefaultEnvFile();
        return fromArgsAndEnv(args, System.getenv(), envPath);
    }

    public static GatewayConfig fromArgsAndEnv(String[] args, Map<String, String> systemEnv, Path envFile) {
        Map<String, String> dotEnv = loadDotEnv(envFile);
        Map<String, String> env = (systemEnv != null) ? systemEnv : Collections.emptyMap();
        Map<String, String> cli = parseCliArgs(args);

        String rawPort = firstNonBlank(
                cli.get("port"),
                env.get("PORT"),
                dotEnv.get("PORT")
        );
        int port = DEFAULT_PORT;
        if (rawPort != null) {
            try {
                port = Integer.parseInt(rawPort.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Giá trị port không hợp lệ: " + rawPort, e);
            }
        }

        String rawMode = firstNonBlank(
                cli.get("mode"),
                env.get("APP_ENV"),
                env.get("MONOPOLY_ENV"),
                dotEnv.get("APP_ENV"),
                dotEnv.get("MONOPOLY_ENV"),
                DEFAULT_MODE
        );

        String originSecret = firstNonBlank(
                cli.get("secret"),
                env.get("ORIGIN_SECRET_KEY"),
                env.get("APP_SECURITY_ORIGIN_SECRET"),
                dotEnv.get("ORIGIN_SECRET_KEY"),
                dotEnv.get("APP_SECURITY_ORIGIN_SECRET")
        );

        String jwtSecret = firstNonBlank(
                cli.get("jwt-secret"),
                env.get("JWT_SECRET"),
                dotEnv.get("JWT_SECRET"),
                DEFAULT_JWT_SECRET
        );

        return new GatewayConfig(port, rawMode, originSecret, jwtSecret, DEFAULT_JWT_VALIDITY_MS);
    }

    public CloudflareOriginFilter createOriginFilter() {
        return CloudflareOriginFilter.forEnvironment(mode, originSecret);
    }

    public int getPort() {
        return port;
    }

    public String getMode() {
        return mode;
    }

    public String getOriginSecret() {
        return originSecret;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public long getJwtValidityMs() {
        return jwtValidityMs;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("Mode không được để trống.");
        }
        String lower = mode.trim().toLowerCase();
        if ("dev".equals(lower) || "development".equals(lower) || "local".equals(lower)) {
            return "dev";
        }
        if ("prod".equals(lower) || "production".equals(lower)) {
            return "prod";
        }
        throw new IllegalArgumentException("Chế độ không hợp lệ (chỉ hỗ trợ dev hoặc prod): " + mode);
    }

    private static Map<String, String> parseCliArgs(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        if (args == null) {
            return result;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq > 0) {
                String key = body.substring(0, eq).trim().toLowerCase();
                String value = body.substring(eq + 1).trim();
                result.put(key, value);
            } else if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                String key = body.trim().toLowerCase();
                result.put(key, args[++i].trim());
            }
        }
        return result;
    }

    private static Path findDefaultEnvFile() {
        Path direct = Path.of(".env");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path parent = Path.of("..", ".env");
        if (Files.isRegularFile(parent)) {
            return parent;
        }
        return null;
    }

    private static Map<String, String> loadDotEnv(Path envFile) {
        Map<String, String> map = new LinkedHashMap<>();
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return map;
        }
        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                if (rawLine == null) continue;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                        || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException ignored) {
            // Bỏ qua nếu không đọc được file .env tùy chọn
        }
        return map;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) return null;
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
