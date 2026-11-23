package server.gateway;

import engine.GameEngine;
import model.GameRoom;
import network.GameMessage;
import server.controller.AuthRestController;
import server.controller.GameWebSocketController;
import server.controller.RoomRestController;
import server.exception.GlobalExceptionHandler;
import server.persistence.UserAccount;
import server.persistence.UserRepository;
import server.security.CloudflareOriginFilter;
import server.security.JwtTokenProvider;
import server.service.RoomManagerService;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API Gateway hợp nhất thuần JDK phục vụ cả HTTP/1.1 REST API và WebSocket (RFC 6455) + STOMP 1.2 trên một cổng TCP.
 */
public class ApiGatewayServer {
    private static final int KEEP_ALIVE_TIMEOUT_MS = 30_000;

    private final GatewayConfig config;
    private final CloudflareOriginFilter originFilter;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RoomManagerService roomManagerService;
    private final AuthRestController authController;
    private final RoomRestController roomController;
    private final GameWebSocketController wsController;
    private final WebSocketStompBroker stompBroker;

    private final Object lifecycleLock = new Object();
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();

    private volatile boolean running = false;
    private volatile int boundPort;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService workerPool;
    private ExecutorService broadcastPool;

    public ApiGatewayServer(GatewayConfig config,
                            CloudflareOriginFilter originFilter,
                            JwtTokenProvider jwtTokenProvider,
                            UserRepository userRepository,
                            RoomManagerService roomManagerService,
                            AuthRestController authController,
                            RoomRestController roomController,
                            GameWebSocketController wsController) {
        if (config == null || originFilter == null || jwtTokenProvider == null
                || userRepository == null || roomManagerService == null
                || authController == null || roomController == null || wsController == null) {
            throw new IllegalArgumentException("Các dependency của ApiGatewayServer không được null.");
        }
        this.config = config;
        this.originFilter = originFilter;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.roomManagerService = roomManagerService;
        this.authController = authController;
        this.roomController = roomController;
        this.wsController = wsController;
        this.stompBroker = new WebSocketStompBroker(jwtTokenProvider, roomManagerService, wsController);
        this.boundPort = config.getPort();
    }

    public ApiGatewayServer(int port,
                            CloudflareOriginFilter originFilter,
                            JwtTokenProvider jwtTokenProvider,
                            UserRepository userRepository,
                            RoomManagerService roomManagerService,
                            AuthRestController authController,
                            RoomRestController roomController,
                            GameWebSocketController wsController) {
        this(
                new GatewayConfig(
                        port,
                        originFilter != null && originFilter.isProtectionEnabled() ? "prod" : "dev",
                        null,
                        GatewayConfig.DEFAULT_JWT_SECRET,
                        GatewayConfig.DEFAULT_JWT_VALIDITY_MS
                ),
                originFilter,
                jwtTokenProvider,
                userRepository,
                roomManagerService,
                authController,
                roomController,
                wsController
        );
    }

    public void start() throws IOException {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            ServerSocket ss = new ServerSocket(config.getPort());
            ss.setReuseAddress(true);
            this.serverSocket = ss;
            this.boundPort = ss.getLocalPort();
            this.workerPool = Executors.newVirtualThreadPerTaskExecutor();
            this.broadcastPool = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "monopoly-snapshot-broadcaster");
                t.setDaemon(true);
                return t;
            });
            this.running = true;

            registerEngineSnapshotHook();

            this.acceptThread = new Thread(this::acceptLoop, "monopoly-gateway-accept");
            this.acceptThread.setDaemon(false);
            this.acceptThread.start();
        }
    }

    public void stop() {
        Thread threadToJoin;
        synchronized (lifecycleLock) {
            if (!running && serverSocket == null) {
                return;
            }
            running = false;
            roomManagerService.setGlobalEngineEventListener(null);

            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                serverSocket = null;
            }

            stompBroker.closeAll();

            for (Socket socket : activeSockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
            activeSockets.clear();

            if (workerPool != null) {
                workerPool.shutdownNow();
                workerPool = null;
            }
            if (broadcastPool != null) {
                broadcastPool.shutdownNow();
                broadcastPool = null;
            }
            threadToJoin = acceptThread;
            acceptThread = null;
        }

        if (threadToJoin != null && Thread.currentThread() != threadToJoin) {
            try {
                threadToJoin.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public int getPort() {
        ServerSocket ss = this.serverSocket;
        if (ss != null && ss.getLocalPort() > 0) {
            return ss.getLocalPort();
        }
        return boundPort;
    }

    public boolean isRunning() {
        return running;
    }

    public WebSocketStompBroker getStompBroker() {
        return stompBroker;
    }

    private void registerEngineSnapshotHook() {
        roomManagerService.setGlobalEngineEventListener((room, event) -> {
            if (room == null || event == null || WebSocketStompBroker.isDirectCommandInProgress()) {
                return;
            }
            GameEngine.EngineEvent.Type type = event.getType();
            if (type == GameEngine.EngineEvent.Type.TURN_TIMED_OUT
                    || type == GameEngine.EngineEvent.Type.BOT_TAKEOVER
                    || type == GameEngine.EngineEvent.Type.EXTRA_ROLL_GRANTED
                    || type == GameEngine.EngineEvent.Type.TURN_ENDED
                    || type == GameEngine.EngineEvent.Type.GAME_FINISHED) {
                ExecutorService pool = this.broadcastPool;
                if (pool != null && !pool.isShutdown() && running) {
                    String roomId = room.getId();
                    pool.execute(() -> stompBroker.broadcastRoomSnapshot(roomId));
                }
            }
        });
    }

    private void acceptLoop() {
        while (running) {
            ServerSocket ss = this.serverSocket;
            if (ss == null || ss.isClosed()) {
                break;
            }
            try {
                Socket client = ss.accept();
                if (!running) {
                    client.close();
                    break;
                }
                ExecutorService pool = this.workerPool;
                if (pool == null || pool.isShutdown()) {
                    client.close();
                    break;
                }
                pool.execute(() -> handleConnection(client));
            } catch (SocketException e) {
                if (!running) {
                    break;
                }
            } catch (IOException ignored) {
                if (!running) {
                    break;
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        activeSockets.add(socket);
        try {
            socket.setTcpNoDelay(true);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
            OutputStream out = socket.getOutputStream();

            while (running && !socket.isClosed()) {
                socket.setSoTimeout(KEEP_ALIVE_TIMEOUT_MS);
                HttpRequestParser.HttpRequest request;
                try {
                    request = HttpRequestParser.parse(in);
                } catch (HttpRequestParser.HttpParseException parseEx) {
                    GameMessage err = new GameMessage(
                            "ERROR", null, null, 0,
                            String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                                    SimpleJson.escape(parseEx.getErrorCode()),
                                    SimpleJson.escape(parseEx.getMessage()))
                    );
                    HttpResponseWriter.writeGameMessage(out, parseEx.getStatusCode(), err, false);
                    break;
                } catch (SocketTimeoutException | EOFException | SocketException e) {
                    break;
                }

                if (request == null) {
                    break;
                }

                boolean keepAlive = request.isKeepAlive();
                String method = request.getMethod();
                String path = request.getPath();

                // 1. CORS preflight (OPTIONS)
                if ("OPTIONS".equals(method)) {
                    HttpResponseWriter.writeNoContent(out, keepAlive);
                    if (!keepAlive) break;
                    continue;
                }

                // 2. Cloudflare Origin Shielding trên mọi HTTP request non-OPTIONS & mọi /ws upgrade
                if (!originFilter.verifyOriginHeader(request.getHeader("x-origin-verify"))) {
                    GameMessage forbidden = new GameMessage(
                            "ERROR",
                            null,
                            null,
                            0,
                            "{\"error\":\"FORBIDDEN_ORIGIN\",\"message\":\"Truy cập trực tiếp bị từ chối. Yêu cầu đi qua Cloudflare.\"}"
                    );
                    HttpResponseWriter.writeGameMessage(out, 403, forbidden, false);
                    break;
                }

                // 3. Whitelist HTTP Method
                if (!"GET".equals(method) && !"POST".equals(method)) {
                    HttpResponseWriter.writeMethodNotAllowed(out, keepAlive);
                    if (!keepAlive) break;
                    continue;
                }

                // 4. WebSocket STOMP endpoint (/ws)
                if ("/ws".equals(path) || path.startsWith("/ws/")) {
                    if (WebSocketStompBroker.isWebSocketUpgrade(request)) {
                        stompBroker.handleUpgrade(socket, in, out, request);
                        break;
                    }
                    GameMessage upgradeErr = new GameMessage(
                            "ERROR", null, null, 0,
                            "{\"error\":\"UPGRADE_REQUIRED\",\"message\":\"Endpoint /ws yêu cầu nâng cấp kết nối WebSocket.\"}"
                    );
                    HttpResponseWriter.writeGameMessage(out, 426, upgradeErr, false);
                    break;
                }

                // 5. Chặn path traversal nếu có
                if (path.contains("..")) {
                    GameMessage notFound = new GameMessage(
                            "ERROR", null, null, 0,
                            "{\"error\":\"NOT_FOUND\",\"message\":\"Không tìm thấy đường dẫn API.\"}"
                    );
                    HttpResponseWriter.writeGameMessage(out, 404, notFound, keepAlive);
                    if (!keepAlive) break;
                    continue;
                }

                // 6. Kiểm tra Content-Type cho POST nếu có khai báo khác application/json
                if ("POST".equals(method)) {
                    String contentType = request.getHeader("content-type");
                    if (contentType != null && !contentType.isBlank()
                            && !contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
                        GameMessage mediaErr = new GameMessage(
                                "ERROR", null, null, 0,
                                "{\"error\":\"UNSUPPORTED_MEDIA_TYPE\",\"message\":\"Content-Type phải là application/json.\"}"
                        );
                        HttpResponseWriter.writeGameMessage(out, 415, mediaErr, keepAlive);
                        if (!keepAlive) break;
                        continue;
                    }
                }

                // 7. Điều phối REST API
                dispatchHttpRoute(request, out, keepAlive);
                if (!keepAlive) {
                    break;
                }
            }
        } catch (IOException ignored) {
            // Đóng kết nối phía client
        } finally {
            activeSockets.remove(socket);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void dispatchHttpRoute(HttpRequestParser.HttpRequest request,
                                   OutputStream out,
                                   boolean keepAlive) throws IOException {
        String method = request.getMethod();
        String path = request.getPath();
        String authHeader = request.getHeader("authorization");

        try {
            // GET /health
            if ("GET".equals(method) && "/health".equals(path)) {
                String healthJson = String.format(
                        "{\"status\":\"UP\",\"mode\":\"%s\",\"cloudflareProtectionEnabled\":%b,\"timestamp\":%d}",
                        SimpleJson.escape(config.getMode()),
                        originFilter.isProtectionEnabled(),
                        System.currentTimeMillis()
                );
                HttpResponseWriter.writeJson(out, 200, healthJson, keepAlive);
                return;
            }

            // POST /api/v1/auth/register | /api/auth/register
            if ("POST".equals(method) && ("/api/v1/auth/register".equals(path) || "/api/auth/register".equals(path))) {
                Map<String, Object> body = parseJsonBody(request, out, keepAlive);
                if (body == null) return;
                String username = SimpleJson.getString(body, "username");
                String password = SimpleJson.getString(body, "password");
                String displayName = SimpleJson.getString(body, "displayName");
                GameMessage res = authController.register(username, password, displayName);
                int status = "ERROR".equals(res.getType()) ? HttpResponseWriter.mapErrorStatus(res) : 201;
                HttpResponseWriter.writeGameMessage(out, status, res, keepAlive);
                return;
            }

            // POST /api/v1/auth/login | /api/auth/login
            if ("POST".equals(method) && ("/api/v1/auth/login".equals(path) || "/api/auth/login".equals(path))) {
                Map<String, Object> body = parseJsonBody(request, out, keepAlive);
                if (body == null) return;
                String username = SimpleJson.getString(body, "username");
                String password = SimpleJson.getString(body, "password");
                GameMessage res = authController.login(username, password);
                int status = "ERROR".equals(res.getType()) ? HttpResponseWriter.mapErrorStatus(res) : 200;
                HttpResponseWriter.writeGameMessage(out, status, res, keepAlive);
                return;
            }

            // GET /api/v1/auth/me | /api/auth/me | /api/v1/users/me
            if ("GET".equals(method) && ("/api/v1/auth/me".equals(path)
                    || "/api/auth/me".equals(path)
                    || "/api/v1/users/me".equals(path))) {
                GameMessage res = authController.getCurrentUserProfile(authHeader);
                int status = "ERROR".equals(res.getType()) ? HttpResponseWriter.mapErrorStatus(res) : 200;
                HttpResponseWriter.writeGameMessage(out, status, res, keepAlive);
                return;
            }

            // GET /api/v1/rooms | /api/rooms (JWT required)
            if ("GET".equals(method) && ("/api/v1/rooms".equals(path) || "/api/rooms".equals(path))) {
                String userId = requireAuthenticatedUserId(authHeader, null, out, keepAlive);
                if (userId == null) return;
                GameMessage res = roomController.getRooms();
                int status = "ERROR".equals(res.getType()) ? HttpResponseWriter.mapErrorStatus(res) : 200;
                HttpResponseWriter.writeGameMessage(out, status, res, keepAlive);
                return;
            }

            // POST /api/v1/rooms | /api/rooms (JWT required)
            if ("POST".equals(method) && ("/api/v1/rooms".equals(path) || "/api/rooms".equals(path))) {
                String hostUserId = requireAuthenticatedUserId(authHeader, null, out, keepAlive);
                if (hostUserId == null) return;
                Map<String, Object> body = parseJsonBody(request, out, keepAlive);
                if (body == null) return;

                String roomName = SimpleJson.getString(body, "roomName");
                int maxPlayers = 4;
                if (body.containsKey("maxPlayers")) {
                    Integer parsedMax = SimpleJson.getInteger(body, "maxPlayers");
                    maxPlayers = (parsedMax != null) ? parsedMax : -1;
                }
                String hostDisplayName = resolveDisplayName(hostUserId, authHeader, null);

                GameMessage res = roomController.createRoom(roomName, maxPlayers, hostUserId, hostDisplayName);
                int status = "ERROR".equals(res.getType()) ? HttpResponseWriter.mapErrorStatus(res) : 201;
                HttpResponseWriter.writeGameMessage(out, status, res, keepAlive);
                return;
            }

            // Room routes: /api/v1/rooms/{roomId}[/action] hoặc /api/rooms/{roomId}[/action]
            RoomRoute roomRoute = parseRoomRoute(path);
            if (roomRoute != null) {
                handleRoomRoute(roomRoute, method, authHeader, request, out, keepAlive);
                return;
            }

            GameMessage notFound = new GameMessage(
                    "ERROR", null, null, 0,
                    "{\"error\":\"NOT_FOUND\",\"message\":\"Không tìm thấy đường dẫn API.\"}"
            );
            HttpResponseWriter.writeGameMessage(out, 404, notFound, keepAlive);
        } catch (Throwable ex) {
            GameMessage err = GlobalExceptionHandler.handleException(null, null, 0, ex);
            HttpResponseWriter.writeGameMessage(out, HttpResponseWriter.mapErrorStatus(err), err, keepAlive);
        }
    }

    private void handleRoomRoute(RoomRoute route,
                                 String method,
                                 String authHeader,
                                 HttpRequestParser.HttpRequest request,
                                 OutputStream out,
                                 boolean keepAlive) throws IOException {
        String roomId = route.roomId();
        String action = route.action();

        if ("GET".equals(method) && (action == null || "snapshot".equals(action))) {
            String userId = requireAuthenticatedUserId(authHeader, roomId, out, keepAlive);
            if (userId == null) return;

            GameEngine engine = roomManagerService.getEngine(roomId);
            if (engine == null) {
                GameMessage err = new GameMessage(
                        "ERROR", userId, roomId, 0,
                        "{\"error\":\"ROOM_NOT_FOUND\",\"message\":\"Không tìm thấy phòng chơi.\"}"
                );
                HttpResponseWriter.writeGameMessage(out, 404, err, keepAlive);
                return;
            }
            GameMessage snapshot = wsController.buildSnapshotMessage(
                    engine,
                    roomManagerService.getHostUserId(roomId)
            );
            HttpResponseWriter.writeGameMessage(out, 200, snapshot, keepAlive);
            return;
        }

        if ("POST".equals(method) && "join".equals(action)) {
            String userId = requireAuthenticatedUserId(authHeader, roomId, out, keepAlive);
            if (userId == null) return;

            Map<String, Object> body = parseJsonBody(request, out, keepAlive);
            if (body == null) return;
            String requestedName = SimpleJson.getString(body, "userName");
            String resolvedName = resolveDisplayName(userId, authHeader, requestedName);

            GameMessage res = roomController.joinRoom(roomId, userId, resolvedName);
            if ("ERROR".equals(res.getType())) {
                HttpResponseWriter.writeGameMessage(out, HttpResponseWriter.mapErrorStatus(res), res, keepAlive);
                return;
            }
            stompBroker.broadcastRoomSnapshot(roomId);
            HttpResponseWriter.writeGameMessage(out, 200, res, keepAlive);
            return;
        }

        if ("POST".equals(method) && "leave".equals(action)) {
            String userId = requireAuthenticatedUserId(authHeader, roomId, out, keepAlive);
            if (userId == null) return;

            GameMessage res = roomController.leaveRoom(roomId, userId);
            if ("ERROR".equals(res.getType())) {
                HttpResponseWriter.writeGameMessage(out, HttpResponseWriter.mapErrorStatus(res), res, keepAlive);
                return;
            }
            if ("ROOM_LEFT".equals(res.getType())) {
                stompBroker.broadcastRoomSnapshot(roomId);
            }
            HttpResponseWriter.writeGameMessage(out, 200, res, keepAlive);
            return;
        }

        if ("POST".equals(method) && "start".equals(action)) {
            String hostUserId = requireAuthenticatedUserId(authHeader, roomId, out, keepAlive);
            if (hostUserId == null) return;

            GameMessage res = WebSocketStompBroker.runWithDirectSnapshotSuppressed(
                    () -> roomController.startGame(roomId, hostUserId)
            );
            if ("ERROR".equals(res.getType())) {
                HttpResponseWriter.writeGameMessage(out, HttpResponseWriter.mapErrorStatus(res), res, keepAlive);
                return;
            }
            stompBroker.broadcastToRoom(roomId, res);
            stompBroker.broadcastRoomSnapshot(roomId);
            HttpResponseWriter.writeGameMessage(out, 200, res, keepAlive);
            return;
        }

        GameMessage notFound = new GameMessage(
                "ERROR", null, roomId, 0,
                "{\"error\":\"NOT_FOUND\",\"message\":\"Không tìm thấy đường dẫn API.\"}"
        );
        HttpResponseWriter.writeGameMessage(out, 404, notFound, keepAlive);
    }

    private String requireAuthenticatedUserId(String authHeader,
                                              String roomId,
                                              OutputStream out,
                                              boolean keepAlive) throws IOException {
        if (authHeader == null || !jwtTokenProvider.validateToken(authHeader)) {
            GameMessage err = new GameMessage(
                    "ERROR", null, roomId, 0,
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Token không hợp lệ hoặc đã hết hạn.\"}"
            );
            HttpResponseWriter.writeGameMessage(out, 401, err, keepAlive);
            return null;
        }
        String userId = jwtTokenProvider.extractUserId(authHeader);
        if (userId == null || userId.isBlank()) {
            GameMessage err = new GameMessage(
                    "ERROR", null, roomId, 0,
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Token thiếu thông tin người dùng.\"}"
            );
            HttpResponseWriter.writeGameMessage(out, 401, err, keepAlive);
            return null;
        }
        return userId;
    }

    private String resolveDisplayName(String userId, String authHeader, String preferredName) {
        if (preferredName != null && !preferredName.isBlank()) {
            return preferredName.trim();
        }
        Optional<UserAccount> account = userRepository.findById(userId);
        if (account.isPresent()) {
            String displayName = account.get().getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            return account.get().getUsername();
        }
        String jwtUsername = jwtTokenProvider.extractUsername(authHeader);
        return (jwtUsername != null && !jwtUsername.isBlank()) ? jwtUsername : userId;
    }

    private Map<String, Object> parseJsonBody(HttpRequestParser.HttpRequest request,
                                              OutputStream out,
                                              boolean keepAlive) throws IOException {
        String bodyStr = request.getBodyString();
        if (bodyStr == null || bodyStr.isBlank()) {
            return Map.of();
        }
        try {
            return SimpleJson.parseObject(bodyStr);
        } catch (IllegalArgumentException e) {
            GameMessage err = new GameMessage(
                    "ERROR", null, null, 0,
                    "{\"error\":\"INVALID_JSON\",\"message\":\"Request body không phải JSON hợp lệ.\"}"
            );
            HttpResponseWriter.writeGameMessage(out, 400, err, keepAlive);
            return null;
        }
    }

    private static RoomRoute parseRoomRoute(String path) {
        String prefix;
        if (path.startsWith("/api/v1/rooms/")) {
            prefix = "/api/v1/rooms/";
        } else if (path.startsWith("/api/rooms/")) {
            prefix = "/api/rooms/";
        } else {
            return null;
        }

        String rest = path.substring(prefix.length());
        if (rest.isEmpty()) {
            return null;
        }
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return new RoomRoute(rest, null);
        }
        String roomId = rest.substring(0, slash);
        String action = rest.substring(slash + 1);
        if (roomId.isEmpty() || action.isEmpty() || action.contains("/")) {
            return null;
        }
        return new RoomRoute(roomId, action);
    }

    private record RoomRoute(String roomId, String action) {
    }
}
