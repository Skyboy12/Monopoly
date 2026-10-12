# Kế hoạch phát triển Webgame Cờ Tỷ Phú

## 1. Mục tiêu

Xây dựng webgame Cờ Tỷ Phú nhiều người chơi theo mô hình client-server authoritative. Backend Java/Spring Boot chịu trách nhiệm toàn bộ luật chơi, xúc xắc, giao dịch và trạng thái ván. Client chỉ gửi lệnh điều khiển và hiển thị dữ liệu do server cung cấp.

Mục tiêu OOP là thể hiện rõ bốn tính chất: trừu tượng, kế thừa, đa hình và đóng gói thông qua domain model, game engine và các bài kiểm thử độc lập.

## 2. Phạm vi MVP(Minimum Viable Product)

- Phòng chơi từ 2 đến 4 người.
- Bàn cờ gồm 40 ô.
- Tạo phòng, tham gia phòng và bắt đầu ván.
- Gieo xúc xắc, di chuyển và xử lý ô cờ.
- Mua đất, trả tiền thuê, trả thuế và xử lý nhà tù.
- Phá sản và xác định người thắng.
- Xử lý timeout, AFK và bot tự động cơ bản.

Chưa triển khai trong MVP:

- Trao đổi tài sản nâng cao giữa người chơi.
- Đấu giá nâng cao.
- Khôi phục ván đang chơi sau khi server khởi động lại.
- Hệ thống xếp hạng hoàn chỉnh.
- Hiệu ứng giao diện nâng cao.

## 3. Công nghệ và kiến trúc

### Backend

- Java 17 trở lên.
- Spring Boot 3.
- Spring Web MVC cho REST API.
- Spring WebSocket/STOMP cho giao tiếp realtime.
- Spring Security, JWT và BCrypt.
- PostgreSQL cho dữ liệu tài khoản.

### Frontend

- React và Vite.
- Giao tiếp bằng WebSocket/STOMP.
- Client chỉ render snapshot/event từ server, không tự tính xúc xắc, tiền thuê, tiền hoặc vị trí.

### Lưu trữ trạng thái

- Database chỉ lưu thông tin tài khoản, display name và rating points.
- Game state lưu trong RAM thông qua các `GameRoom` đang hoạt động.
- Khi server restart, các ván đang chơi sẽ mất trong phạm vi MVP.

### Kiến trúc mạng và Tầng Edge (Edge Gateway / Firewall)

- **Cloudflare Worker**: Đóng vai trò là Reverse Proxy và "Tường lửa" (Edge Firewall) đứng trước Origin server. Chịu trách nhiệm drop các request không hợp lệ (path quét lỗ hổng, sai HTTP method, spam rate limit, header rác, payload ngoại cỡ) ngay tại Edge trước khi chuyển tiếp về Spring Boot.
- **Origin Shielding**: Spring Boot chỉ chấp nhận traffic chuyển tiếp từ Cloudflare Worker thông qua secret token header (`X-Origin-Verify`). Không cho phép truy cập trực tiếp bypass qua Origin IP.

## 4. Thiết kế domain OOP

### 4.1. Các lớp chính

- `GameRoom`: aggregate root, quản lý người chơi, bàn cờ, lượt hiện tại, trạng thái phòng, turn ID và timer.
- `Player`: quản lý tiền, vị trí, tài sản, trạng thái nhà tù, AFK và phá sản.
- `Board`: chứa danh sách các `Tile` theo thứ tự trên bàn cờ.
- `Dice`: sinh kết quả xúc xắc bằng `SecureRandom`.

### 4.2. Trừu tượng

Tạo `abstract class Tile` để mô tả hành vi chung của một ô cờ. Game engine chỉ gọi hành vi của tile, không cần biết tile cụ thể thuộc loại nào.

```java
public abstract void onLand(Player player, GameRoom room);
```

Tạo interface hoặc strategy cho card action, cách tính tiền thuê và quyết định của bot.

### 4.3. Kế thừa

Phân cấp các ô cờ gồm:

- `PropertyTile`
- `StreetProperty`
- `RailroadProperty`
- `UtilityProperty`
- `TaxTile`
- `GoTile`
- `JailTile`
- `GoToJailTile`
- `FreeParkingTile`

`PropertyTile` chứa các thuộc tính chung như giá mua, chủ sở hữu và trạng thái cầm cố. Các lớp con triển khai quy tắc tính tiền thuê riêng.

### 4.4. Đa hình

Khi người chơi dừng tại một vị trí, game engine chỉ cần gọi:

```java
board.getTile(position).onLand(player, room);
```

Mỗi loại tile tự xử lý hành vi tương ứng. Game loop không dùng `if-else` để kiểm tra loại ô.

### 4.5. Đóng gói

Các thuộc tính của `Player` và `GameRoom` phải là `private`. Thay đổi tiền, tài sản, vị trí hoặc trạng thái chỉ được thực hiện qua các method nghiệp vụ như:

- `addMoney(...)`
- `pay(...)`
- `buyProperty(...)`
- `transferProperty(...)`
- `declareBankruptcy(...)`

Controller không được sửa trực tiếp domain state.

### 4.6. Enum và value object

Tạo enum hoặc value object cho:

- `RoomStatus`
- `TurnPhase`
- `PlayerStatus`
- `ActionType`
- Kết quả giao dịch và lỗi nghiệp vụ.

## 5. Authoritative game engine

1. Server tự sinh kết quả xúc xắc bằng `SecureRandom`.
2. Server kiểm tra `roomId`, `playerId`, `turnId`, phase và quyền của người gửi.
3. Server cập nhật vị trí và xử lý tile bằng polymorphism.
4. Server xử lý mua đất, tiền thuê, thuế, nhà tù, mortgage và phá sản.
5. Server tạo transaction/action result bất biến.
6. Server broadcast event hoặc snapshot mới cho các client trong phòng.

Client không được gửi hoặc áp dụng trực tiếp `money`, `position`, `dice result` hay `owner`.

## 6. State machine và vòng đời lượt chơi

Các trạng thái dự kiến:

- `WAITING`
- `PLAYING`
- `RESOLVING_ACTION`
- `AWAITING_DECISION`
- `TIMEOUT`
- `FINISHED`

Mỗi action phải được kiểm tra theo trạng thái hiện tại. Action dùng `turnId` cũ, sai người chơi, sai phase hoặc gửi trùng sẽ bị từ chối.

## 7. REST API và WebSocket

### REST API

- Đăng ký và đăng nhập.
- Xem thông tin cá nhân.
- Tạo phòng.
- Xem danh sách phòng.
- Tham gia hoặc rời phòng.
- Bắt đầu ván khi đủ điều kiện.

### WebSocket/STOMP

Các command chính:

- `ROLL_DICE`
- `BUY_PROPERTY`
- `END_TURN`
- `PAY_BAIL`
- `USE_CARD`

Xác định rõ DTO request/response, event type, error code và snapshot state. Không expose trực tiếp domain object ra API.

## 8. Bảo mật, anti-cheat và Tường lửa Edge

### 8.1. Bảo mật Backend & Game Engine
- Dùng BCrypt để hash mật khẩu; không lưu password plaintext hoặc salt riêng.
- Dùng JWT cho REST API và WebSocket handshake.
- Phân quyền theo user, room và host.
- Không tin bất kỳ state nào do client gửi lên (authoritative server).
- Từ chối action không hợp lệ và action có turn ID cũ.
- Database chỉ bind trong mạng LAN/local; không port-forward database ra Internet.

### 8.2. "Tường lửa" Cloudflare Worker (Edge Firewall)
Thiết lập Cloudflare Worker đứng phía trước làm Edge Firewall để thanh lọc và drop toàn bộ request bất thường trước khi chạm tới Spring Boot:
- **Lọc đường dẫn (Path Whitelisting & Scanner Drop)**:
  - Chỉ cho phép các path hợp lệ: `/api/**`, `/ws/**` và static frontend.
  - Drop ngay lập tức (HTTP 403/404) các request quét mã độc phổ biến: `.php`, `.env`, `.git`, `wp-admin`, `phpmyadmin`, `cgi-bin`, directory traversal (`../`).
- **Lọc HTTP Method**:
  - Chỉ cho phép `GET`, `POST`, `OPTIONS` (và upgrade `GET` cho WebSocket).
  - Drop toàn bộ các method không dùng: `PUT`, `DELETE`, `TRACE`, `CONNECT`, `PATCH`.
- **Giới hạn kích thước Payload (Content-Length)**:
  - Drop ngay các request có `Content-Length > 100KB` (chống DoS / tràn bộ nhớ backend do request rác).
  - Ép buộc `Content-Type: application/json` đối với các request `POST` vào `/api/**`.
- **Pre-validation JWT Token & Headers**:
  - Kiểm tra định dạng `Authorization: Bearer <header>.<payload>.<signature>` tại Edge cho các route cần xác thực. Nếu token rỗng hoặc sai cấu trúc base64url thì drop ngay 401.
  - Kiểm tra `User-Agent` hợp lệ; drop các bot crawl rác hoặc scanner đã biết.
- **Rate Limiting & Anti-Spam**:
  - Giới hạn tần suất request per IP đối với các endpoint nhạy cảm (đăng ký `/api/auth/register`, đăng nhập `/api/auth/login`, tạo phòng `/api/rooms`).
- **Origin Shielding (Header X-Origin-Verify)**:
  - Worker tự động chèn header bí mật `X-Origin-Verify: <SECRET_KEY>` khi chuyển tiếp request về Spring Boot.
  - Spring Boot cấu hình một servlet filter (`CloudflareOriginFilter`) để từ chối mọi request trực tiếp không có hoặc sai secret key này (tránh lộ IP gốc).

## 9. Đồng thời, timeout và bot

- Quản lý phòng bằng `ConcurrentHashMap<String, GameRoom>`.
- Serialize command trong từng `GameRoom`, không khóa toàn bộ hệ thống.
- Dùng `ScheduledExecutorService` hoặc Spring Scheduler cho turn timer.
- Broadcast sự kiện `TURN_START_TIMER` khi bắt đầu lượt.
- Ngay lần timeout đầu tiên, chuyển người chơi sang `BotController` hoặc `BotStrategy` để xử lý lượt hiện tại; các lượt sau vẫn khởi động timer và chỉ để bot xử lý sau khi lượt đó timeout.
- Bot tự gieo xúc xắc, mua đất khi có ít nhất ba lần giá đất và xử lý action bắt buộc.
- Người chơi có thể bật auto thủ công và giành lại quyền bằng action hợp lệ.
- Kiểm tra race condition giữa timeout, reconnect, action trùng lặp và nhiều command đồng thời.

## 10. Persistence và frontend

### Database

Tạo migration/schema cho:

- User ID.
- Username.
- Password hash.
- Display name.
- Rating points.

### Frontend

Các màn hình chính:

- Login/register.
- Lobby.
- Room.
- Bàn cờ.
- Thông tin người chơi.
- Turn timer.
- Action controls.
- Thông báo lỗi.
- Màn hình kết thúc.

Frontend phải khóa hoặc mở control dựa trên phase và quyền hiện tại do server gửi.

## 11. Kiểm thử

### Unit test

- `Player` và các giao dịch tiền.
- `Board` và từng loại `Tile`.
- Dice và rent calculation.
- Nhà tù, phá sản và bot strategy.
- Polymorphism của `onLand(...)`.

### Integration test

- Authentication.
- REST lobby.
- WebSocket command validation.
- Broadcast snapshot.
- Reconnect.
- Timeout.

### Concurrency test

- Hai command đồng thời trong cùng một room.
- Nhiều room hoạt động đồng thời.
- Duplicate action.
- Action từ người chơi không có lượt.
- Không chuyển lượt hai lần hoặc gán property cho hai người.

## 12. Lộ trình thực hiện

### Giai đoạn 1: Phân tích và thiết kế

- Chốt luật MVP.
- Vẽ UML class diagram.
- Vẽ sequence diagram cho các luồng chính.
- Chốt REST/WebSocket contract.

### Giai đoạn 2: Core OOP

- Implement `Player`, `GameRoom`, `Board`, `Tile`.
- Implement các subclass của `Tile`.
- Implement giao dịch tiền, tài sản và phá sản.

### Giai đoạn 3: Authoritative engine

- Implement turn state machine.
- Implement dice bằng `SecureRandom`.
- Implement validation và event generation.

### Giai đoạn 4: API và bảo mật

- Implement authentication.
- Implement REST lobby.
- Implement WebSocket/STOMP.
- Implement reconnect và snapshot.

### Giai đoạn 5: AFK và bot

- Implement timer.
- Implement timeout.
- Implement bot strategy.
- Kiểm thử concurrency.

### Giai đoạn 6: Frontend

- Implement login, lobby và room.
- Render bàn cờ và trạng thái người chơi.
- Kết nối WebSocket.
- Hoàn thiện action controls và thông báo lỗi.

### Giai đoạn 7: Kiểm thử và triển khai LAN

- Chạy unit/integration/concurrency test.
- Test nhiều browser/client.
- Test mất kết nối và reconnect.
- Viết tài liệu setup, migration và giới hạn MVP.

### Giai đoạn 8: Triển khai Internet, Edge Firewall và hoàn thiện

- Xây dựng Cloudflare Worker script đóng vai trò "Tường lửa" lọc request ở Edge.
- Thiết lập quy tắc drop các request quét mã độc (`.env`, `.php`, scanners), method lạ, payload quá khổ (> 100KB), header rác và token sai định dạng.
- Tích hợp bộ lọc `CloudflareOriginFilter` trên Spring Boot để xác minh header bí mật `X-Origin-Verify`, chặn truy cập trực tiếp bypass vào IP gốc.
- Thiết lập Cloudflare Tunnel (hoặc Port Forwarding an toàn) kết nối từ server về Cloudflare Edge, không để lộ IP mạng nhà.
- Cấu hình Domain name, SSL/TLS (Full/Strict) và WebSocket proxy.
- Kiểm thử "bắn" request độc hại/rác thử nghiệm từ Internet và kiểm tra log drop tại Cloudflare Worker.
- Giám sát server, log lỗi và độ trễ WebSocket.

## 13. Cấu trúc thư mục dự kiến

```text
backend/src/main/java/.../domain
backend/src/main/java/.../engine
backend/src/main/java/.../web
backend/src/main/java/.../security
backend/src/test/java/...
frontend/src/...
cloudflare-firewall/
  ├── wrangler.toml
  ├── package.json
  └── src/
      └── index.ts
docs/
  ├── plan-summary.md
  ├── event-and-item.md
  └── cloudflare-worker-firewall.md
```

## 14. Tiêu chí nghiệm thu

- UML thể hiện đầy đủ abstraction, inheritance, polymorphism và encapsulation.
- Server là nguồn sự thật duy nhất của game state.
- Client không thể tự thay đổi tiền, vị trí, xúc xắc hoặc chủ sở hữu tài sản.
- Các action sai người chơi, sai lượt, sai phase hoặc sai turn ID đều bị từ chối.
- Tường lửa Cloudflare Worker drop thành công 100% request rác, path scanner, spam brute-force và method không hợp lệ tại Edge mà không làm quá tải Spring Boot.
- Backend Spring Boot từ chối mọi request trực tiếp không đi qua Cloudflare Worker (thiếu `X-Origin-Verify`).
- Nhiều client trong cùng phòng nhận được state nhất quán qua WebSocket.
- Timeout và bot không làm treo vòng chơi.
- Unit test, integration test và concurrency test đạt yêu cầu.
- Tài liệu setup, cấu hình firewall và giới hạn MVP khớp với hành vi thực tế.