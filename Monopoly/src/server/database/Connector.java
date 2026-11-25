package server.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Connector {
    private static final String DEFAULT_DB_NAME = "monopoly";
    private static final int MAX_POOL_SIZE = 8;
    private static final String JDBC_PARAMS_NO_SSL =
            "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=10000";
    private static final String JDBC_PARAMS_SSL =
            "useSSL=true&requireSSL=true&verifyServerCertificate=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=10000";
    private static final Map<String, String> DOTENV = loadDotEnv();
    private static final ArrayBlockingQueue<Connection> CONNECTION_POOL = new ArrayBlockingQueue<>(MAX_POOL_SIZE);

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found. Ensure mysql-connector-j is in lib/", e);
        }
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> env = new HashMap<>();
        File[] candidates = {
            new File(".env"),
            new File("../.env"),
            new File("/etc/monopoly/monopoly.env")
        };
        for (File file : candidates) {
            if (file.exists() && file.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        if (line.startsWith("export ")) {
                            line = line.substring(7).trim();
                        }
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String val = line.substring(idx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2)
                                    || (val.startsWith("'") && val.endsWith("'") && val.length() >= 2)) {
                                val = val.substring(1, val.length() - 1);
                            }
                            env.put(key, val);
                        }
                    }
                } catch (IOException ignored) {
                }
                break;
            }
        }
        return env;
    }

    private static String getEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        val = DOTENV.get(key);
        return (val != null && !val.isBlank()) ? val.trim() : defaultValue;
    }

    public static boolean isConfigured() {
        return !getEnv("DATABASE", "").isBlank();
    }

    private static ParsedTarget parseTarget() {
        String raw = getEnv("DATABASE", "localhost:3306");
        String defaultDb = sanitizeDbName(getEnv("DATABASE_NAME", DEFAULT_DB_NAME));

        String cleaned = raw.trim();
        if (cleaned.startsWith("jdbc:mysql://")) {
            cleaned = cleaned.substring("jdbc:mysql://".length());
        } else if (cleaned.startsWith("tcp://")) {
            cleaned = cleaned.substring("tcp://".length());
        } else if (cleaned.startsWith("mysql://")) {
            cleaned = cleaned.substring("mysql://".length());
        }

        String existingQuery = "";
        int qIdx = cleaned.indexOf('?');
        if (qIdx >= 0) {
            existingQuery = cleaned.substring(qIdx + 1);
            cleaned = cleaned.substring(0, qIdx);
        }

        String hostPort = cleaned;
        String dbName = defaultDb;
        int slashIdx = cleaned.indexOf('/');
        if (slashIdx >= 0) {
            hostPort = cleaned.substring(0, slashIdx);
            String tail = cleaned.substring(slashIdx + 1).trim();
            if (!tail.isEmpty()) {
                dbName = sanitizeDbName(tail);
            }
        }
        if (hostPort.isBlank()) {
            hostPort = "localhost:3306";
        }
        boolean useSsl = "true".equalsIgnoreCase(getEnv("DATABASE_SSL", "false"));
        String defaultParams = useSsl ? JDBC_PARAMS_SSL : JDBC_PARAMS_NO_SSL;
        String params = existingQuery.isBlank() ? defaultParams : existingQuery;
        return new ParsedTarget(hostPort, dbName, params);
    }

    private static String sanitizeDbName(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return DEFAULT_DB_NAME;
        }
        String sanitized = dbName.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isEmpty() ? DEFAULT_DB_NAME : sanitized;
    }

    private static String buildServerJdbcUrl() {
        ParsedTarget t = parseTarget();
        return "jdbc:mysql://" + t.hostPort + "/?" + t.params;
    }

    private static String buildJdbcUrl() {
        ParsedTarget t = parseTarget();
        return "jdbc:mysql://" + t.hostPort + "/" + t.dbName + "?" + t.params;
    }

    public static String getSafeConnectionSummary() {
        ParsedTarget t = parseTarget();
        return t.hostPort + "/" + t.dbName;
    }

    public static Connection getConnection() throws SQLException {
        while (true) {
            Connection pooled = CONNECTION_POOL.poll();
            if (pooled == null) {
                break;
            }
            try {
                if (!pooled.isClosed() && pooled.isValid(2)) {
                    return wrapPooledConnection(pooled);
                }
            } catch (SQLException ignored) {
            }
            closeQuietly(pooled);
        }
        return wrapPooledConnection(createRawConnection());
    }

    private static Connection createRawConnection() throws SQLException {
        String url = buildJdbcUrl();
        String user = getEnv("DATABASE_USER", "root");
        String pass = getEnv("DATABASE_PASS", "");
        return DriverManager.getConnection(url, user, pass);
    }

    private static Connection wrapPooledConnection(Connection raw) {
        AtomicBoolean closed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name) && method.getParameterCount() == 0) {
                        if (closed.compareAndSet(false, true)) {
                            releaseToPool(raw);
                        }
                        return null;
                    }
                    if ("isClosed".equals(name) && method.getParameterCount() == 0) {
                        return closed.get() || raw.isClosed();
                    }
                    if (closed.get()) {
                        throw new SQLException("Pooled connection is already closed.");
                    }
                    try {
                        return method.invoke(raw, args);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getTargetException();
                        if (cause != null) {
                            throw cause;
                        }
                        throw e;
                    }
                }
        );
    }

    private static void releaseToPool(Connection raw) {
        if (raw == null) {
            return;
        }
        try {
            if (!raw.isClosed()) {
                if (!raw.getAutoCommit()) {
                    raw.setAutoCommit(true);
                }
                if (raw.isValid(2) && CONNECTION_POOL.offer(raw)) {
                    return;
                }
            }
        } catch (SQLException ignored) {
        }
        closeQuietly(raw);
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public static void closePool() {
        Connection conn;
        while ((conn = CONNECTION_POOL.poll()) != null) {
            closeQuietly(conn);
        }
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public static void initSchema() throws SQLException {
        ParsedTarget target = parseTarget();
        String user = getEnv("DATABASE_USER", "root");
        String pass = getEnv("DATABASE_PASS", "");

        try (Connection serverConn = DriverManager.getConnection(buildServerJdbcUrl(), user, pass);
             Statement serverStmt = serverConn.createStatement()) {
            serverStmt.execute("CREATE DATABASE IF NOT EXISTS `" + target.dbName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL UNIQUE,
                display_name VARCHAR(100) NULL,
                password_hash VARCHAR(128) NOT NULL,
                elo INT NOT NULL DEFAULT 1000,
                wins INT NOT NULL DEFAULT 0,
                losses INT NOT NULL DEFAULT 0,
                games_played INT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_elo (elo DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        String createMatchHistoryTable = """
            CREATE TABLE IF NOT EXISTS match_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                winner_id INT NOT NULL,
                loser_id INT NOT NULL,
                winner_elo_before INT NOT NULL,
                winner_elo_after INT NOT NULL,
                loser_elo_before INT NOT NULL,
                loser_elo_after INT NOT NULL,
                played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (loser_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createMatchHistoryTable);
            ensureDisplayNameColumn(conn, target.dbName);
        }
    }

    private static void ensureDisplayNameColumn(Connection conn, String dbName) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'users' AND COLUMN_NAME = 'display_name'";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement alter = conn.createStatement()) {
                        alter.execute("ALTER TABLE users ADD COLUMN display_name VARCHAR(100) NULL AFTER username");
                    }
                }
            }
        }
    }

    private record ParsedTarget(String hostPort, String dbName, String params) {
    }
}
