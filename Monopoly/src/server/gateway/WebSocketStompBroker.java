package server.gateway;

import engine.GameEngine;
import model.GameRoom;
import model.Player;
import model.item.ItemTargetContext;
import network.GameMessage;
import server.controller.GameWebSocketController;
import server.exception.GlobalExceptionHandler;
import server.security.JwtTokenProvider;
import server.service.RoomManagerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Broker WebSocket (RFC 6455) + STOMP 1.2 thuần JDK.
 * Hỗ trợ handshake SHA-1, protocol negotiation (@stomp/stompjs), frame mask/unmask/fragmentation/ping-pong/close,
 * xác thực JWT bắt buộc khi CONNECT/STOMP và ủy quyền tuyệt đối qua GameWebSocketController.
 */
public class WebSocketStompBroker {
    private static final String WS_MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_WS_PAYLOAD_BYTES = 1024 * 1024; // 1 MB
    private static final List<String> SUPPORTED_WS_PROTOCOLS = List.of("v12.stomp", "v11.stomp", "v10.stomp", "stomp");
    private static final ThreadLocal<Integer> DIRECT_COMMAND_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final JwtTokenProvider jwtTokenProvider;
    private final RoomManagerService roomManagerService;
    private final GameWebSocketController wsController;

    private final Set<StompSession> sessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SubscriptionEntry>> destinationSubscribers =
            new ConcurrentHashMap<>();
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final ScheduledExecutorService heartbeatScheduler;

    public WebSocketStompBroker(JwtTokenProvider jwtTokenProvider,
                                RoomManagerService roomManagerService,
                                GameWebSocketController wsController) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.roomManagerService = roomManagerService;
        this.wsController = wsController;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monopoly-ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeatScheduler.scheduleAtFixedRate(this::runHeartbeatTick, 5, 5, TimeUnit.SECONDS);
    }

    public static boolean isDirectCommandInProgress() {
        return DIRECT_COMMAND_DEPTH.get() > 0;
    }

    public static <T> T runWithDirectSnapshotSuppressed(Supplier<T> action) {
        int current = DIRECT_COMMAND_DEPTH.get();
        DIRECT_COMMAND_DEPTH.set(current + 1);
        try {
            return action.get();
        } finally {
            if (current == 0) {
                DIRECT_COMMAND_DEPTH.remove();
            } else {
                DIRECT_COMMAND_DEPTH.set(current);
            }
        }
    }

    public static boolean isWebSocketUpgrade(HttpRequestParser.HttpRequest request) {
        if (request == null || !"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String upgrade = request.getHeader("upgrade");
        String connection = request.getHeader("connection");
        return upgrade != null && upgrade.toLowerCase(Locale.ROOT).contains("websocket")
                && connection != null && connection.toLowerCase(Locale.ROOT).contains("upgrade");
    }

    public void handleUpgrade(Socket socket,
                              InputStream in,
                              OutputStream out,
                              HttpRequestParser.HttpRequest request) throws IOException {
        String wsKey = request.getHeader("sec-websocket-key");
        if (wsKey == null || wsKey.isBlank()) {
            GameMessage err = new GameMessage(
                    "ERROR", null, null, 0,
                    "{\"error\":\"BAD_WEBSOCKET_HANDSHAKE\",\"message\":\"Thiếu header Sec-WebSocket-Key.\"}"
            );
            HttpResponseWriter.writeGameMessage(out, 400, err, false);
            return;
        }

        String acceptKey = computeAcceptKey(wsKey.trim());
        String negotiatedProtocol = negotiateProtocol(request.getHeader("sec-websocket-protocol"));
        HttpResponseWriter.writeWebSocketUpgrade(out, acceptKey, negotiatedProtocol);

        socket.setSoTimeout(0);
        StompSession session = new StompSession(socket, out, request.getQueryParams().get("token"));
        sessions.add(session);

        try {
            readFrameLoop(session, in);
        } finally {
            cleanupSession(session);
        }
    }

    public void broadcastToRoom(String roomId, GameMessage message) {
        if (roomId == null || message == null) {
            return;
        }
        String destination = "/topic/room/" + roomId;
        publishToDestination(destination, SimpleJson.toJson(message));
    }

    public void broadcastRoomSnapshot(String roomId) {
        if (roomId == null) {
            return;
        }
        GameEngine engine = roomManagerService.getEngine(roomId);
        if (engine == null) {
            return;
        }
        GameMessage snapshot = wsController.buildSnapshotMessage(
                engine,
                roomManagerService.getHostUserId(roomId),
                roomManagerService.getTurnDeadlineEpochMs(roomId),
                roomManagerService.getRoomLogs(roomId),
                roomManagerService.getTurnTimeoutSeconds()
        );
        broadcastToRoom(roomId, snapshot);
    }

    public void sendToPlayerErrors(String userId, GameMessage errorMessage) {
        if (userId == null || errorMessage == null) {
            return;
        }
        String destination = "/queue/player/" + userId + "/errors";
        publishToDestination(destination, SimpleJson.toJson(errorMessage));
    }

    public void closeAll() {
        heartbeatScheduler.shutdownNow();
        for (StompSession session : new ArrayList<>(sessions)) {
            try {
                sendCloseFrame(session, 1001, "Server shutting down");
            } catch (IOException ignored) {
            }
            cleanupSession(session);
            try {
                session.socket.close();
            } catch (IOException ignored) {
            }
        }
        sessions.clear();
        destinationSubscribers.clear();
    }

    private void readFrameLoop(StompSession session, InputStream in) throws IOException {
        ByteArrayOutputStream fragmentBuffer = new ByteArrayOutputStream();
        boolean expectingContinuation = false;

        while (!session.closed && !session.socket.isClosed()) {
            int b0 = in.read();
            if (b0 == -1) {
                break;
            }
            boolean fin = (b0 & 0x80) != 0;
            int rsv = b0 & 0x70;
            int opcode = b0 & 0x0F;

            if (rsv != 0) {
                sendCloseFrame(session, 1002, "Unsupported RSV bits");
                break;
            }

            int b1 = in.read();
            if (b1 == -1) {
                break;
            }
            boolean masked = (b1 & 0x80) != 0;
            int len7 = b1 & 0x7F;

            boolean isControlFrame = (opcode & 0x08) != 0;
            if (isControlFrame && (!fin || len7 > 125)) {
                sendCloseFrame(session, 1002, "Invalid control frame");
                break;
            }

            long payloadLenLong;
            if (len7 == 126) {
                int hi = in.read();
                int lo = in.read();
                if (hi == -1 || lo == -1) break;
                payloadLenLong = ((hi & 0xFF) << 8) | (lo & 0xFF);
            } else if (len7 == 127) {
                byte[] lenBytes = readExact(in, 8);
                if (lenBytes == null) break;
                long val = 0;
                for (int i = 0; i < 8; i++) {
                    val = (val << 8) | (lenBytes[i] & 0xFFL);
                }
                payloadLenLong = val;
            } else {
                payloadLenLong = len7;
            }

            if (payloadLenLong < 0 || payloadLenLong > MAX_WS_PAYLOAD_BYTES) {
                sendCloseFrame(session, 1009, "Payload too large");
                break;
            }

            if (!masked) {
                sendCloseFrame(session, 1002, "Client frames must be masked");
                break;
            }

            byte[] maskKey = readExact(in, 4);
            if (maskKey == null) {
                break;
            }

            int payloadLen = (int) payloadLenLong;
            byte[] payload = readExact(in, payloadLen);
            if (payload == null) {
                break;
            }
            for (int i = 0; i < payloadLen; i++) {
                payload[i] = (byte) (payload[i] ^ maskKey[i & 3]);
            }
            session.lastReadMs = System.currentTimeMillis();

            switch (opcode) {
                case 0x8 -> { // CLOSE
                    int closeCode = 1000;
                    if (payload.length >= 2) {
                        closeCode = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    }
                    sendCloseFrame(session, closeCode, "");
                    return;
                }
                case 0x9 -> // PING
                        sendWebSocketFrame(session, 0x0A, payload);
                case 0xA -> { // PONG
                    // Bỏ qua
                }
                case 0x1, 0x2 -> { // TEXT / BINARY
                    if (expectingContinuation) {
                        sendCloseFrame(session, 1002, "Unexpected data frame during fragmentation");
                        return;
                    }
                    fragmentBuffer.reset();
                    fragmentBuffer.write(payload);
                    if (fin) {
                        processWebSocketPayload(session, fragmentBuffer.toByteArray());
                        fragmentBuffer.reset();
                    } else {
                        expectingContinuation = true;
                    }
                }
                case 0x0 -> { // CONTINUATION
                    if (!expectingContinuation) {
                        sendCloseFrame(session, 1002, "Unexpected continuation frame");
                        return;
                    }
                    if (fragmentBuffer.size() + payload.length > MAX_WS_PAYLOAD_BYTES) {
                        sendCloseFrame(session, 1009, "Fragmented message too large");
                        return;
                    }
                    fragmentBuffer.write(payload);
                    if (fin) {
                        expectingContinuation = false;
                        processWebSocketPayload(session, fragmentBuffer.toByteArray());
                        fragmentBuffer.reset();
                    }
                }
                default -> {
                    sendCloseFrame(session, 1002, "Unsupported opcode");
                    return;
                }
            }
        }
    }

    private void processWebSocketPayload(StompSession session, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }
        int idx = 0;
        while (idx < data.length) {
            // Bỏ qua heartbeat (\n hoặc \r\n)
            while (idx < data.length && (data[idx] == '\n' || data[idx] == '\r')) {
                idx++;
            }
            if (idx >= data.length) {
                return;
            }

            int lineEnd = findByte(data, idx, (byte) '\n');
            if (lineEnd == -1) {
                return;
            }
            String command = decodeLine(data, idx, lineEnd).trim().toUpperCase(Locale.ROOT);
            idx = lineEnd + 1;
            if (command.isEmpty()) {
                continue;
            }

            Map<String, String> headers = new LinkedHashMap<>();
            while (idx < data.length) {
                int nextLf = findByte(data, idx, (byte) '\n');
                if (nextLf == -1) {
                    idx = data.length;
                    break;
                }
                String headerLine = decodeLine(data, idx, nextLf);
                idx = nextLf + 1;
                if (headerLine.isEmpty()) {
                    break;
                }
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    String key = headerLine.substring(0, colon);
                    String val = unescapeStompHeader(headerLine.substring(colon + 1));
                    headers.putIfAbsent(key, val);
                }
            }

            String contentLengthHeader = getHeaderCaseInsensitive(headers, "content-length");
            String body;
            if (contentLengthHeader != null) {
                int contentLength;
                try {
                    contentLength = Integer.parseInt(contentLengthHeader.trim());
                } catch (NumberFormatException e) {
                    sendStompErrorAndClose(session, "Invalid content-length", null,
                            "{\"error\":\"BAD_STOMP_FRAME\",\"message\":\"Header content-length không hợp lệ.\"}");
                    return;
                }
                if (contentLength < 0 || idx + contentLength > data.length) {
                    sendStompErrorAndClose(session, "Invalid content-length", null,
                            "{\"error\":\"BAD_STOMP_FRAME\",\"message\":\"Độ dài body STOMP không khớp content-length.\"}");
                    return;
                }
                body = new String(data, idx, contentLength, StandardCharsets.UTF_8);
                idx += contentLength;
                if (idx < data.length && data[idx] == 0) {
                    idx++;
                }
            } else {
                int nulIdx = findByte(data, idx, (byte) 0);
                if (nulIdx == -1) {
                    body = new String(data, idx, data.length - idx, StandardCharsets.UTF_8);
                    idx = data.length;
                } else {
                    body = new String(data, idx, nulIdx - idx, StandardCharsets.UTF_8);
                    idx = nulIdx + 1;
                }
            }

            handleStompFrame(session, command, headers, body);
            if (session.closed) {
                return;
            }
        }
    }

    private void handleStompFrame(StompSession session,
                                  String command,
                                  Map<String, String> headers,
                                  String body) throws IOException {
        if ("CONNECT".equals(command) || "STOMP".equals(command)) {
            handleConnect(session, headers);
            return;
        }

        if (!session.authenticated) {
            sendStompErrorAndClose(session, "Unauthorized", getHeaderCaseInsensitive(headers, "receipt"),
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Phiên STOMP chưa xác thực.\"}");
            return;
        }

        switch (command) {
            case "SUBSCRIBE" -> handleSubscribe(session, headers);
            case "UNSUBSCRIBE" -> handleUnsubscribe(session, headers);
            case "SEND" -> handleSend(session, headers, body);
            case "DISCONNECT" -> handleDisconnect(session, headers);
            case "ACK", "NACK" -> {
                String receipt = getHeaderCaseInsensitive(headers, "receipt");
                if (receipt != null && !receipt.isBlank()) {
                    sendReceipt(session, receipt);
                }
            }
            default -> sendStompErrorAndClose(session, "Unsupported command",
                    getHeaderCaseInsensitive(headers, "receipt"),
                    "{\"error\":\"UNSUPPORTED_STOMP_COMMAND\",\"message\":\"Lệnh STOMP không được hỗ trợ.\"}");
        }
    }

    private void handleConnect(StompSession session, Map<String, String> headers) throws IOException {
        String authHeader = getHeaderCaseInsensitive(headers, "authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = getHeaderCaseInsensitive(headers, "passcode");
        }
        if ((authHeader == null || authHeader.isBlank()) && session.queryToken != null && !session.queryToken.isBlank()) {
            authHeader = session.queryToken;
        }

        if (authHeader == null || !jwtTokenProvider.validateToken(authHeader)) {
            sendStompErrorAndClose(session, "Unauthorized", null,
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Token JWT không hợp lệ hoặc bị thiếu khi kết nối STOMP.\"}");
            return;
        }

        String userId = jwtTokenProvider.extractUserId(authHeader);
        if (userId == null || userId.isBlank()) {
            sendStompErrorAndClose(session, "Unauthorized", null,
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Không thể xác định người dùng từ token JWT.\"}");
            return;
        }

        session.authenticated = true;
        session.userId = userId;
        session.rawAuthToken = authHeader;

        int[] hb = negotiateHeartbeat(getHeaderCaseInsensitive(headers, "heart-beat"));
        session.serverHbMs = hb[0];
        session.clientHbMs = hb[1];

        String acceptVersion = getHeaderCaseInsensitive(headers, "accept-version");
        String version = negotiateStompVersion(acceptVersion);

        String connected = "CONNECTED\n"
                + "version:" + version + "\n"
                + "heart-beat:" + hb[0] + "," + hb[1] + "\n"
                + "session:" + session.sessionId + "\n"
                + "user-name:" + userId + "\n"
                + "\n\0";
        sendTextFrame(session, connected);
    }

    private static int[] negotiateHeartbeat(String headerVal) {
        if (headerVal == null || headerVal.isBlank()) {
            return new int[]{0, 0};
        }
        String[] parts = headerVal.trim().split(",");
        if (parts.length != 2) {
            return new int[]{0, 0};
        }
        try {
            int cx = Math.max(0, Integer.parseInt(parts[0].trim()));
            int cy = Math.max(0, Integer.parseInt(parts[1].trim()));
            if (cx == 0 && cy == 0) {
                return new int[]{0, 0};
            }
            int sx = cy > 0 ? Math.max(cy, 10_000) : 0;
            int sy = cx > 0 ? Math.max(cx, 10_000) : 0;
            return new int[]{sx, sy};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    private void handleSubscribe(StompSession session, Map<String, String> headers) throws IOException {
        String subId = getHeaderCaseInsensitive(headers, "id");
        String destination = getHeaderCaseInsensitive(headers, "destination");
        String receipt = getHeaderCaseInsensitive(headers, "receipt");

        if (destination == null || destination.isBlank()) {
            sendStompErrorAndClose(session, "Missing destination", receipt,
                    "{\"error\":\"INVALID_DESTINATION\",\"message\":\"Thiếu destination trong lệnh SUBSCRIBE.\"}");
            return;
        }
        if (subId == null || subId.isBlank()) {
            subId = "sub-" + UUID.randomUUID().toString().substring(0, 8);
        }

        if (destination.startsWith("/queue/player/") && destination.endsWith("/errors")) {
            String targetUserId = destination.substring(
                    "/queue/player/".length(),
                    destination.length() - "/errors".length()
            );
            if (!targetUserId.equals(session.userId)) {
                sendStompErrorAndClose(session, "Forbidden subscription", receipt,
                        "{\"error\":\"FORBIDDEN_SUBSCRIPTION\",\"message\":\"Không có quyền đăng ký kênh lỗi của người chơi khác.\"}");
                return;
            }
        }

        if (destination.startsWith("/topic/room/")) {
            String roomId = destination.substring("/topic/room/".length());
            if (!roomId.isBlank() && !roomId.contains("/")) {
                GameRoom room = roomManagerService.getRoom(roomId);
                if (room != null) {
                    boolean isMember = false;
                    synchronized (room) {
                        for (Player p : room.getPlayers()) {
                            if (session.userId.equals(p.getId())) {
                                isMember = true;
                                break;
                            }
                        }
                    }
                    if (!isMember) {
                        sendStompErrorAndClose(session, "Forbidden subscription", receipt,
                                "{\"error\":\"FORBIDDEN_SUBSCRIPTION\",\"message\":\"Bạn không phải thành viên của phòng này.\"}");
                        return;
                    }
                }
            }
        }

        SubscriptionEntry entry = new SubscriptionEntry(session, subId, destination);
        SubscriptionEntry previous = session.subscriptions.put(subId, entry);
        if (previous != null) {
            removeFromDestinationIndex(previous);
        }
        destinationSubscribers
                .computeIfAbsent(destination, ignored -> new CopyOnWriteArrayList<>())
                .add(entry);

        if (receipt != null && !receipt.isBlank()) {
            sendReceipt(session, receipt);
        }

        if (destination.startsWith("/topic/room/")) {
            String roomId = destination.substring("/topic/room/".length());
            if (!roomId.isBlank() && !roomId.contains("/")) {
                GameEngine engine = roomManagerService.getEngine(roomId);
                if (engine != null) {
                    GameMessage snapshot = wsController.buildSnapshotMessage(
                            engine,
                            roomManagerService.getHostUserId(roomId),
                            roomManagerService.getTurnDeadlineEpochMs(roomId),
                            roomManagerService.getRoomLogs(roomId),
                            roomManagerService.getTurnTimeoutSeconds()
                    );
                    sendStompMessage(entry, SimpleJson.toJson(snapshot));
                }
            }
        }
    }

    private void handleUnsubscribe(StompSession session, Map<String, String> headers) throws IOException {
        String subId = getHeaderCaseInsensitive(headers, "id");
        if (subId != null) {
            SubscriptionEntry removed = session.subscriptions.remove(subId);
            if (removed != null) {
                removeFromDestinationIndex(removed);
            }
        }
        String receipt = getHeaderCaseInsensitive(headers, "receipt");
        if (receipt != null && !receipt.isBlank()) {
            sendReceipt(session, receipt);
        }
    }

    private void handleSend(StompSession session, Map<String, String> headers, String body) throws IOException {
        String destination = getHeaderCaseInsensitive(headers, "destination");
        String receipt = getHeaderCaseInsensitive(headers, "receipt");
        String userId = session.userId;

        if (session.rawAuthToken == null || !jwtTokenProvider.validateToken(session.rawAuthToken)) {
            sendStompErrorAndClose(session, "Unauthorized", receipt,
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Token JWT đã hết hạn hoặc không hợp lệ.\"}");
            return;
        }

        if (receipt != null && !receipt.isBlank()) {
            sendReceipt(session, receipt);
        }

        if (destination == null || !destination.startsWith("/app/room/")) {
            sendToPlayerErrors(userId, new GameMessage(
                    "ERROR", userId, null, 0,
                    "{\"error\":\"INVALID_DESTINATION\",\"message\":\"Đích STOMP SEND không hợp lệ.\"}"
            ));
            return;
        }

        String rest = destination.substring("/app/room/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            sendToPlayerErrors(userId, new GameMessage(
                    "ERROR", userId, null, 0,
                    "{\"error\":\"INVALID_DESTINATION\",\"message\":\"Đích STOMP SEND không hợp lệ.\"}"
            ));
            return;
        }

        String roomId = rest.substring(0, slash);
        String action = rest.substring(slash + 1);

        Map<String, Object> bodyMap;
        try {
            bodyMap = (body == null || body.isBlank()) ? Map.of() : SimpleJson.parseObject(body);
        } catch (IllegalArgumentException e) {
            sendToPlayerErrors(userId, new GameMessage(
                    "ERROR", userId, roomId, 0,
                    "{\"error\":\"INVALID_JSON\",\"message\":\"Payload STOMP không phải JSON hợp lệ.\"}"
            ));
            return;
        }

        int turnId = SimpleJson.getInt(bodyMap, "turnId", -1);
        GameEngine engine = roomManagerService.getEngine(roomId);
        if (engine == null) {
            sendToPlayerErrors(userId, new GameMessage(
                    "ERROR", userId, roomId, turnId,
                    "{\"error\":\"ROOM_NOT_FOUND\",\"message\":\"Không tìm thấy phòng chơi.\"}"
            ));
            return;
        }

        GameMessage result = runWithDirectSnapshotSuppressed(() -> {
            try {
                return switch (action) {
                    case "roll-dice" -> wsController.handleRollDice(roomId, userId, turnId, engine);
                    case "buy-property" -> {
                        int tileIndex = SimpleJson.getInt(bodyMap, "tileIndex", -1);
                        yield wsController.handleBuyProperty(roomId, userId, turnId, tileIndex, engine);
                    }
                    case "use-item" -> {
                        String itemId = SimpleJson.getString(bodyMap, "itemId");
                        ItemTargetContext ctx = extractItemContext(bodyMap);
                        yield wsController.handleUseItemWithContext(roomId, userId, turnId, itemId, ctx, engine);
                    }
                    case "end-turn" -> wsController.handleEndTurn(roomId, userId, turnId, engine);
                    case "pay-bail" -> wsController.handlePayBail(roomId, userId, turnId, engine);
                    case "upgrade-property" -> {
                        int tileIndex = SimpleJson.getInt(bodyMap, "tileIndex", -1);
                        yield wsController.handleUpgradeProperty(roomId, userId, turnId, tileIndex, engine);
                    }
                    default -> new GameMessage(
                            "ERROR", userId, roomId, turnId,
                            "{\"error\":\"UNKNOWN_ACTION\",\"message\":\"Hành động STOMP không được hỗ trợ.\"}"
                    );
                };
            } catch (Throwable ex) {
                return GlobalExceptionHandler.handleException(roomId, userId, turnId, ex);
            }
        });

        if (result == null) {
            return;
        }

        if ("ERROR".equals(result.getType())) {
            sendToPlayerErrors(userId, result);
        } else {
            recordDirectCommandLog(roomId, result);
            broadcastToRoom(roomId, result);
            broadcastRoomSnapshot(roomId);
        }
    }

    private void recordDirectCommandLog(String roomId, GameMessage result) {
        if (roomId == null || result == null || result.getType() == null) {
            return;
        }
        Map<String, Object> payloadMap;
        try {
            String rawPayload = result.getPayload();
            payloadMap = (rawPayload == null || rawPayload.isBlank())
                    ? Map.of()
                    : SimpleJson.parseObject(rawPayload);
        } catch (IllegalArgumentException ignored) {
            payloadMap = Map.of();
        }

        switch (result.getType()) {
            case "DICE_ROLLED" -> {
                String actorName = resolvePlayerName(roomId, result.getSenderId());
                int d1 = SimpleJson.getInt(payloadMap, "dice1", 0);
                int d2 = SimpleJson.getInt(payloadMap, "dice2", 0);
                int total = SimpleJson.getInt(payloadMap, "total", d1 + d2);
                int newPos = SimpleJson.getInt(payloadMap, "newPosition", 0);
                String code = SimpleJson.getString(payloadMap, "code");
                String suffix = "THREE_DOUBLES".equals(code)
                        ? " (Gieo đôi 3 lần -> Vào tù!)"
                        : " -> Ô #" + newPos;
                roomManagerService.appendRoomLog(
                        roomId,
                        String.format("%s gieo được %d + %d = %d%s", actorName, d1, d2, total, suffix)
                );
                if ("GAME_FINISHED".equals(code)) {
                    roomManagerService.appendRoomLog(roomId, "Ván đấu đã kết thúc!");
                }
            }
            case "PROPERTY_BOUGHT" -> {
                String ownerId = SimpleJson.getString(payloadMap, "ownerId");
                String actorName = resolvePlayerName(roomId, ownerId != null ? ownerId : result.getSenderId());
                String tileName = SimpleJson.getString(payloadMap, "tileName");
                int price = SimpleJson.getInt(payloadMap, "price", 0);
                roomManagerService.appendRoomLog(
                        roomId,
                        String.format("%s đã mua %s với giá $%d.",
                                actorName,
                                tileName != null ? tileName : "tài sản",
                                price)
                );
            }
            case "PROPERTY_UPGRADED" -> {
                String ownerId = SimpleJson.getString(payloadMap, "ownerId");
                String actorName = resolvePlayerName(roomId, ownerId != null ? ownerId : result.getSenderId());
                String tileName = SimpleJson.getString(payloadMap, "tileName");
                int cost = SimpleJson.getInt(payloadMap, "cost", 0);
                boolean hasHotel = Boolean.TRUE.equals(SimpleJson.getBoolean(payloadMap, "hasHotel"));
                int houseCount = SimpleJson.getInt(payloadMap, "houseCount", 0);
                String levelLabel = hasHotel ? "khách sạn" : ("nhà cấp " + houseCount);
                roomManagerService.appendRoomLog(
                        roomId,
                        String.format("%s đã nâng cấp %s lên %s ($%d).",
                                actorName,
                                tileName != null ? tileName : "ô đất",
                                levelLabel,
                                cost)
                );
            }
            case "ITEM_USED" -> {
                String actorId = SimpleJson.getString(payloadMap, "actorId");
                String actorName = resolvePlayerName(roomId, actorId != null ? actorId : result.getSenderId());
                String itemName = SimpleJson.getString(payloadMap, "itemName");
                if (itemName == null || itemName.isBlank()) {
                    itemName = SimpleJson.getString(payloadMap, "itemId");
                }
                roomManagerService.appendRoomLog(
                        roomId,
                        String.format("%s đã kích hoạt vật phẩm %s.",
                                actorName,
                                itemName != null ? itemName : "")
                );
            }
            case "BAIL_PAID" -> {
                String playerId = SimpleJson.getString(payloadMap, "playerId");
                String actorName = resolvePlayerName(roomId, playerId != null ? playerId : result.getSenderId());
                roomManagerService.appendRoomLog(
                        roomId,
                        actorName + " đã nộp $50 bảo lãnh ra tù."
                );
            }
            case "TURN_CHANGED" -> {
                String code = SimpleJson.getString(payloadMap, "code");
                String nextPlayerId = SimpleJson.getString(payloadMap, "currentPlayerId");
                String nextPlayerName = resolvePlayerName(roomId, nextPlayerId);
                int newTurnId = SimpleJson.getInt(payloadMap, "newTurnId", result.getTurnId());
                String roomStatus = SimpleJson.getString(payloadMap, "roomStatus");
                if ("EXTRA_ROLL".equals(code)) {
                    roomManagerService.appendRoomLog(
                            roomId,
                            nextPlayerName + " gieo đôi và được tung thêm lượt!"
                    );
                } else {
                    roomManagerService.appendRoomLog(
                            roomId,
                            String.format("Chuyển lượt sang %s (Turn #%d).", nextPlayerName, newTurnId)
                    );
                }
                if ("FINISHED".equals(roomStatus)) {
                    roomManagerService.appendRoomLog(roomId, "Ván đấu đã kết thúc!");
                }
            }
            default -> {
            }
        }
    }

    private String resolvePlayerName(String roomId, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return "Hệ thống";
        }
        GameRoom room = roomManagerService.getRoom(roomId);
        if (room != null) {
            synchronized (room) {
                for (Player p : room.getPlayers()) {
                    if (playerId.equals(p.getId())) {
                        String name = p.getName();
                        return (name != null && !name.isBlank()) ? name : playerId;
                    }
                }
            }
        }
        return playerId;
    }

    private void handleDisconnect(StompSession session, Map<String, String> headers) throws IOException {
        String receipt = getHeaderCaseInsensitive(headers, "receipt");
        if (receipt != null && !receipt.isBlank()) {
            sendReceipt(session, receipt);
        }
        sendCloseFrame(session, 1000, "Disconnected");
        session.closed = true;
    }

    private static ItemTargetContext extractItemContext(Map<String, Object> bodyMap) {
        Map<String, Object> ctxMap = SimpleJson.getObject(bodyMap, "context");
        Map<String, Object> source = (ctxMap != null) ? ctxMap : bodyMap;
        String targetPlayerId = SimpleJson.getString(source, "targetPlayerId");
        Integer targetTileIndex = SimpleJson.getInteger(source, "targetTileIndex");
        Integer desiredDiceValue = SimpleJson.getInteger(source, "desiredDiceValue");
        return new ItemTargetContext(targetPlayerId, targetTileIndex, desiredDiceValue);
    }

    private void publishToDestination(String destination, String jsonBody) {
        CopyOnWriteArrayList<SubscriptionEntry> entries = destinationSubscribers.get(destination);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (SubscriptionEntry entry : entries) {
            if (entry.session.closed || entry.session.socket.isClosed()) {
                continue;
            }
            try {
                sendStompMessage(entry, jsonBody);
            } catch (IOException ignored) {
                cleanupSession(entry.session);
            }
        }
    }

    private void sendStompMessage(SubscriptionEntry entry, String jsonBody) throws IOException {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        String msgId = "msg-" + messageSeq.getAndIncrement();
        String frame = "MESSAGE\n"
                + "subscription:" + entry.subscriptionId + "\n"
                + "message-id:" + msgId + "\n"
                + "destination:" + entry.destination + "\n"
                + "content-type:application/json;charset=utf-8\n"
                + "content-length:" + bodyBytes.length + "\n"
                + "\n"
                + jsonBody
                + "\0";
        sendTextFrame(entry.session, frame);
    }

    private void sendReceipt(StompSession session, String receiptId) throws IOException {
        String frame = "RECEIPT\nreceipt-id:" + receiptId + "\n\n\0";
        sendTextFrame(session, frame);
    }

    private void sendStompErrorAndClose(StompSession session,
                                        String messageHeader,
                                        String receiptId,
                                        String jsonBody) throws IOException {
        byte[] bodyBytes = (jsonBody == null ? "" : jsonBody).getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR\n");
        if (messageHeader != null) {
            sb.append("message:").append(messageHeader).append('\n');
        }
        if (receiptId != null && !receiptId.isBlank()) {
            sb.append("receipt-id:").append(receiptId).append('\n');
        }
        sb.append("content-type:application/json;charset=utf-8\n");
        sb.append("content-length:").append(bodyBytes.length).append('\n');
        sb.append('\n');
        if (jsonBody != null) {
            sb.append(jsonBody);
        }
        sb.append('\0');
        sendTextFrame(session, sb.toString());
        sendCloseFrame(session, 1008, messageHeader != null ? messageHeader : "STOMP Error");
        session.closed = true;
    }

    private void sendTextFrame(StompSession session, String text) throws IOException {
        sendWebSocketFrame(session, 0x01, text.getBytes(StandardCharsets.UTF_8));
    }

    private void sendCloseFrame(StompSession session, int code, String reason) throws IOException {
        byte[] reasonBytes = (reason == null) ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        int maxReason = Math.min(reasonBytes.length, 123);
        byte[] payload = new byte[2 + maxReason];
        payload[0] = (byte) ((code >> 8) & 0xFF);
        payload[1] = (byte) (code & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, maxReason);
        synchronized (session.writeLock) {
            if (!session.closeSent && !session.socket.isClosed()) {
                session.closeSent = true;
                writeFrameUnchecked(session.out, 0x08, payload);
            }
        }
    }

    private void sendWebSocketFrame(StompSession session, int opcode, byte[] payload) throws IOException {
        synchronized (session.writeLock) {
            if (session.closed || session.socket.isClosed()) {
                return;
            }
            writeFrameUnchecked(session.out, opcode, payload);
            session.lastWriteMs = System.currentTimeMillis();
        }
    }

    private void runHeartbeatTick() {
        long now = System.currentTimeMillis();
        for (StompSession session : new ArrayList<>(sessions)) {
            if (session.closed || session.socket.isClosed()) {
                cleanupSession(session);
                continue;
            }
            long deadTimeout = (session.clientHbMs > 0)
                    ? Math.max(45_000L, session.clientHbMs * 3L)
                    : 90_000L;
            if (now - session.lastReadMs > deadTimeout) {
                try {
                    sendCloseFrame(session, 1001, "Heartbeat timeout");
                } catch (IOException ignored) {
                }
                cleanupSession(session);
                try {
                    session.socket.close();
                } catch (IOException ignored) {
                }
                continue;
            }
            if (!session.authenticated) {
                continue;
            }
            try {
                if (session.serverHbMs > 0 && now - session.lastWriteMs >= session.serverHbMs) {
                    sendTextFrame(session, "\n");
                } else if (session.serverHbMs == 0 && now - session.lastWriteMs >= 25_000L) {
                    sendWebSocketFrame(session, 0x09, new byte[0]);
                }
            } catch (IOException ignored) {
                cleanupSession(session);
            }
        }
    }

    private static void writeFrameUnchecked(OutputStream out, int opcode, byte[] payload) throws IOException {
        byte[] safePayload = (payload == null) ? new byte[0] : payload;
        int len = safePayload.length;
        int headerSize = (len <= 125) ? 2 : (len <= 65535 ? 4 : 10);
        byte[] frame = new byte[headerSize + len];
        frame[0] = (byte) (0x80 | (opcode & 0x0F));
        if (len <= 125) {
            frame[1] = (byte) len;
        } else if (len <= 65535) {
            frame[1] = 126;
            frame[2] = (byte) ((len >> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
        } else {
            frame[1] = 127;
            long longLen = len;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) ((longLen >>> ((7 - i) * 8)) & 0xFF);
            }
        }
        System.arraycopy(safePayload, 0, frame, headerSize, len);
        out.write(frame);
        out.flush();
    }

    private void cleanupSession(StompSession session) {
        session.closed = true;
        sessions.remove(session);
        for (SubscriptionEntry entry : session.subscriptions.values()) {
            removeFromDestinationIndex(entry);
        }
        session.subscriptions.clear();
    }

    private void removeFromDestinationIndex(SubscriptionEntry entry) {
        CopyOnWriteArrayList<SubscriptionEntry> list = destinationSubscribers.get(entry.destination);
        if (list != null) {
            list.remove(entry);
            if (list.isEmpty()) {
                destinationSubscribers.remove(entry.destination, list);
            }
        }
    }

    private static String computeAcceptKey(String wsKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((wsKey + WS_MAGIC_GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thiếu thuật toán SHA-1 trong JDK", e);
        }
    }

    private static String negotiateProtocol(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        List<String> requested = new ArrayList<>();
        for (String token : headerValue.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                requested.add(trimmed);
            }
        }
        for (String supported : SUPPORTED_WS_PROTOCOLS) {
            for (String clientProto : requested) {
                if (supported.equalsIgnoreCase(clientProto)) {
                    return clientProto;
                }
            }
        }
        return requested.isEmpty() ? null : requested.get(0);
    }

    private static String negotiateStompVersion(String acceptVersion) {
        if (acceptVersion == null || acceptVersion.isBlank()) {
            return "1.2";
        }
        for (String candidate : List.of("1.2", "1.1", "1.0")) {
            for (String token : acceptVersion.split(",")) {
                if (candidate.equals(token.trim())) {
                    return candidate;
                }
            }
        }
        return "1.2";
    }

    private static String getHeaderCaseInsensitive(Map<String, String> headers, String key) {
        if (headers == null || key == null) return null;
        String direct = headers.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String unescapeStompHeader(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 'c' -> sb.append(':');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int findByte(byte[] data, int fromIndex, byte target) {
        for (int i = fromIndex; i < data.length; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static String decodeLine(byte[] data, int start, int lfIndex) {
        int end = lfIndex;
        if (end > start && data[end - 1] == '\r') {
            end--;
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        if (length == 0) {
            return new byte[0];
        }
        byte[] buf = in.readNBytes(length);
        if (buf.length < length) {
            return null;
        }
        return buf;
    }

    private static final class StompSession {
        private final String sessionId = UUID.randomUUID().toString();
        private final Socket socket;
        private final OutputStream out;
        private final String queryToken;
        private final Object writeLock = new Object();
        private final Map<String, SubscriptionEntry> subscriptions = new ConcurrentHashMap<>();
        private volatile boolean authenticated = false;
        private volatile String userId = null;
        private volatile String rawAuthToken = null;
        private volatile long lastReadMs = System.currentTimeMillis();
        private volatile long lastWriteMs = System.currentTimeMillis();
        private volatile int clientHbMs = 0;
        private volatile int serverHbMs = 0;
        private volatile boolean closed = false;
        private volatile boolean closeSent = false;

        private StompSession(Socket socket, OutputStream out, String queryToken) {
            this.socket = socket;
            this.out = out;
            this.queryToken = queryToken;
        }
    }

    private record SubscriptionEntry(StompSession session, String subscriptionId, String destination) {
    }
}
