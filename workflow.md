# Git Workflow & Pull Request Guideline (Monopoly Webgame)

Tài liệu này quy chuẩn toàn bộ quy trình làm việc trên GitHub theo mô hình **Fork & Pull Request (PR)**, chuẩn báo cáo khi tạo PR, cùng danh mục Domain Model và REST API / WebSocket STOMP dành cho thành viên tiếp nối phát triển dự án.

---

## 1. Quy trình Fork - Branch - Pull Request

Mô hình **Fork & Pull Request** giúp bảo vệ nhánh chính (`main`/`develop`) của repository gốc (Upstream), đảm bảo mọi tính năng, sửa lỗi đều qua code review và kiểm thử trước khi hợp nhất.

```
+-------------------------------------------------------------+
|             UPSTREAM REPOSITORY (Dự án gốc)                 |
|                   [main]  <---  [develop]                   |
+------------------------------^------------------------------+
                               |
                   PR (Code Review & Merge)
                               |
+------------------------------+------------------------------+
|             FORK REPOSITORY (Repo cá nhân)                  |
|                   [feature/my-task]                         |
+------------------------------^------------------------------+
                               | (git push)
+------------------------------+------------------------------+
|                     LOCAL WORKSPACE                         |
+-------------------------------------------------------------+
```

### Bước 1: Fork và Clone Repository
1. Truy cập repository gốc của nhóm trên GitHub (Upstream).
2. Nhấn nút **Fork** (ở góc trên bên phải) về tài khoản cá nhân.
3. Clone repository cá nhân (Origin) về máy:
   ```bash
   git clone https://github.com/<your-username>/Monopoly.git
   cd Monopoly
   ```

### Bước 2: Thiết lập Remote Upstream & Đồng bộ
Cấu hình remote trỏ về repository gốc để liên tục cập nhật code mới nhất từ nhóm:
```bash
# Kiểm tra remotes hiện có
git remote -v

# Thêm remote upstream
git remote add upstream https://github.com/<org-hoac-chu-repo>/Monopoly.git

# Kiểm tra lại (sẽ thấy cả origin và upstream)
git remote -v
```

### Bước 3: Tạo nhánh làm việc (Feature Branch)
Luôn đồng bộ nhánh `main`/`develop` mới nhất trước khi tách nhánh:
```bash
git checkout main
git fetch upstream
git merge upstream/main

# Tạo và chuyển sang nhánh mới theo chuẩn đặt tên
git checkout -b feature/board-oop-engine
```

#### Quy ước đặt tên nhánh (Branch Naming Convention):
- `feature/<tên-chức-năng>`: Thêm tính năng mới (ví dụ: `feature/jwt-auth`, `feature/dice-engine`).
- `bugfix/<lỗi-cần-sửa>`: Sửa lỗi (ví dụ: `bugfix/jail-turn-skip`, `bugfix/tax-calculation`).
- `refactor/<khối-tối-ưu>`: Tái cấu trúc mã nguồn (ví dụ: `refactor/room-concurrency`).
- `docs/<tài-liệu>`: Cập nhật tài liệu, README, workflow (ví dụ: `docs/api-specs`).
- `test/<khối-test>`: Viết unit test / integration test (ví dụ: `test/player-bankruptcy`).

### Bước 4: Commit và Push mã nguồn
- Viết commit message rõ ràng, tuân thủ **Conventional Commits**:
  - `feat: triển khai logic onLand cho StreetProperty`
  - `fix: xử lý race condition khi 2 người mua cùng ô đất`
  - `test: bổ sung test case cho phá sản và chuyển nhượng tài sản`
  - `refactor: đóng gói các thuộc tính tiền và vị trí trong Player`
- Đẩy nhánh lên fork của bạn:
  ```bash
  git push -u origin feature/board-oop-engine
  ```

### Bước 5: Mở Pull Request (PR)
1. Truy cập repo cá nhân trên GitHub, nhấn **Compare & pull request**.
2. **Base repository**: chọn `upstream` - nhánh `main` (hoặc `develop`).
3. **Head repository**: chọn `origin` - nhánh `feature/board-oop-engine`.
4. Điền tiêu đề và **mẫu báo cáo PR hoàn chỉnh** (chi tiết ở Mục 2).
5. Gán Reviewer và chờ phê duyệt (yêu cầu ít nhất 1 Reviewer duyệt và CI pass trước khi Merge).

---

## 2. Tiêu chuẩn Báo cáo Hoàn chỉnh của Pull Request (PR Template)

> Mỗi PR khi mở bắt buộc phải điền đầy đủ mẫu báo cáo bên dưới vào phần mô tả (Description) để đảm bảo người review và người làm sau nắm bắt trọn vẹn context.

```markdown
### 1. Tổng quan & Mục tiêu PR
- **Loại thay đổi**: [ ] Feature | [ ] Bugfix | [ ] Refactor | [ ] Test | [ ] Docs
- **Mô tả ngắn**: (Giải thích ngắn gọn 1-3 câu PR này giải quyết bài toán gì)
- **Issue liên quan**: Closes #... (nếu có)

### 2. Chi tiết các thay đổi chính (What was changed?)
- **Domain Model / OOP**:
  - Thêm/sửa class: ...
  - Tính chất OOP áp dụng (Abstraction, Inheritance, Polymorphism, Encapsulation): ...
- **API / WebSocket**:
  - Endpoint REST mới/sửa: ...
  - WebSocket event/command: ...
- **Tầng dữ liệu / Cấu hình**:
  - Schema DB thay đổi (nếu có): ...
  - Thư viện / Config mới: ...

### 3. Hướng dẫn kiểm thử & Xác thực (How to test?)
1. Khởi chạy backend & môi trường test.
2. Bước 1: ...
3. Bước 2: ...
4. Kết quả mong đợi (Expected Output): ...

### 4. Kết quả Unit Test / Integration Test
- [ ] Số lượng test case bổ sung: ...
- [ ] Kết quả chạy test: `Pass all ... tests`
*(Đính kèm log hoặc ảnh chụp màn hình kiểm thử)*

### 5. Lưu ý cho người phát triển tiếp theo (Handoff Notes)
- Những điểm cần chú ý khi gọi Domain / Service này: ...
- Việc còn tồn đọng hoặc cần làm tiếp ở task sau: ...

### 6. Checklist trước khi Merge
- [ ] Code tuân thủ coding conventions và OOP principles.
- [ ] Không có file rác, file nhạy cảm (.env, IDE config, token bí mật).
- [ ] Không sửa trực tiếp domain state ngoài nghiệp vụ đóng gói.
- [ ] Đã rebase / merge code mới nhất từ upstream.
- [ ] Đã kiểm tra không làm vỡ các module hiện có.
```

---

## 3. Danh mục Domain Model (Hệ thống Hướng đối tượng)

Dành cho thành viên kế thừa để hiểu rõ luồng nghiệp vụ và cách tương tác đúng quy tắc OOP.

```
               +---------------------------------------+
               |               GameRoom                |
               |  (Aggregate Root - Quản lý Ván chơi)  |
               +---------------------------------------+
                | 1                                 | 1
                | 2..4                              | 1
       +--------v---------+                +--------v---------+
       |      Player      |                |      Board       |
       | (Tài sản, Tiền)  |                |   (40 Ô cờ)      |
       +------------------+                +--------+---------+
                                                    | 1..40
                                           +--------v---------+
                                           |  <<abstract>>    |
                                           |       Tile       |
                                           +--------+---------+
                                                    |
          +--------------------+--------------------+--------------------+
          |                    |                    |                    |
+---------v---------+  +-------v--------+  +--------v--------+  +--------v--------+
|   PropertyTile    |  |    TaxTile     |  |    JailTile     |  | Go / GoToJail   |
| (Đất, Ga, Nước/Điện)| (Thuế cố định/%)|  |  (Thăm / Ở tù)  |  |  (Ô di chuyển)  |
+-------------------+  +----------------+  +-----------------+  +-----------------+
```

### 3.1. Các Lớp Thực thể chính
| Lớp (Class) | Phạm vi & Trách nhiệm | Nguyên tắc OOP then chốt |
| :--- | :--- | :--- |
| `GameRoom` | **Aggregate Root**: Quản lý vòng đời phòng, danh sách người chơi, lượt đi (`currentTurn`), trạng thái phòng (`RoomStatus`), timer và bot. | **Encapsulation**: Chỉ room mới được kích hoạt chuyển phase, xoay lượt và kết thúc ván. |
| `Player` | Đại diện người chơi: Lưu trữ `money`, `position`, `status` (ACTIVE, IN_JAIL, BANKRUPT), danh sách tài sản sở hữu. | **Encapsulation**: Nghiêm cấm gán trực tiếp thuộc tính. Mọi thay đổi qua method `addMoney()`, `pay()`, `buyProperty()`, `declareBankruptcy()`. |
| `Board` | Chứa danh sách 40 ô cờ `Tile` có thứ tự index từ 0 đến 39. | **High Cohesion**: Cung cấp truy xuất ô theo index: `board.getTile(position)`. |
| `Dice` | Sinh kết quả ngẫu nhiên 2 xúc xắc bằng `SecureRandom`. Kiểm tra xúc xắc đôi (doubles). | **Authoritative Server**: Sinh hoàn toàn phía server, client không được can thiệp. |

### 3.2. Đa hình và Kế thừa Ô cờ (`Tile`)
- Lớp trừu tượng cơ sở:
  ```java
  public abstract class Tile {
      private final int index;
      private final String name;
      
      public abstract void onLand(Player player, GameRoom room);
  }
  ```
- **Các lớp kế thừa chuyên biệt**:
  - `PropertyTile` (Abstract): Cơ sở cho các ô có thể mua bán/cầm cố (Street, Railroad, Utility).
    - `StreetProperty`: Đất thông thường, tính tiền thuê theo số nhà/khách sạn và combo cùng màu.
    - `RailroadProperty`: Bến xe/nhà ga, tiền thuê nhân đôi theo số lượng ga cùng sở hữu.
    - `UtilityProperty`: Công ty điện/nước, tiền thuê nhân theo giá trị xúc xắc vừa gieo.
  - `TaxTile`: Ô đóng thuế (Income Tax, Luxury Tax).
  - `GoTile`: Ô xuất phát, nhận tiền thưởng qua vòng ($200).
  - `JailTile`: Ô thăm tù hoặc đang thụ án.
  - `GoToJailTile`: Di chuyển thẳng người chơi vào tù và chuyển trạng thái `IN_JAIL`.
  - `FreeParkingTile`: Ô bãi đỗ xe miễn phí (nghỉ ngơi an toàn).

> **Lưu ý cốt lõi khi phát triển tiếp**: Khi xử lý di chuyển của người chơi đến ô mới, **tuyệt đối không dùng `if-else / switch-case` để phân loại ô**, chỉ gọi trực tiếp:
> ```java
> Tile currentTile = room.getBoard().getTile(player.getPosition());
> currentTile.onLand(player, room);
> ```

---

## 4. Danh mục API Dành Cho Người Phát Triển Tiếp Theo

### 4.1. Kiến trúc Bảo mật & Tầng Mạng (Network & Edge Layer)
- **Tường lửa Edge (Cloudflare Worker)**: Mọi request đi từ client bắt buộc qua Cloudflare Worker trước khi đến Spring Boot.
  - Header bí mật: `X-Origin-Verify: <SECRET_KEY>` được Worker inject. Filter tại backend (`CloudflareOriginFilter`) sẽ drop ngay request trực tiếp nếu thiếu header này.
  - Kích thước payload tối đa: `100 KB` (chống tràn bộ nhớ / DoS).
  - Token Auth: Header `Authorization: Bearer <jwt_token>`.

---

### 4.2. REST API (Quản lý Phiên & Sảnh Chờ)

#### Base URL: `/api/v1`

| Phương thức | Endpoint | Yêu cầu Auth | Payload (Request Body) | Phản hồi (Response Body) | Mô tả |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `POST` | `/auth/register` | Không | `{"username": "...", "password": "...", "displayName": "..."}` | `{"userId": "...", "username": "..."}` | Đăng ký tài khoản mới. Mật khẩu hash BCrypt. |
| `POST` | `/auth/login` | Không | `{"username": "...", "password": "..."}` | `{"token": "eyJhbGciOi...", "userId": "...", "displayName": "..."}` | Đăng nhập nhận JWT Token. |
| `GET` | `/users/me` | Có | Không | `{"userId": "...", "username": "...", "rating": 1200}` | Lấy thông tin tài khoản hiện tại. |
| `GET` | `/rooms` | Có | Không | `[{"roomId": "...", "roomName": "...", "playerCount": 2, "maxPlayers": 4, "status": "WAITING"}]` | Lấy danh sách phòng chờ. |
| `POST` | `/rooms` | Có | `{"roomName": "...", "maxPlayers": 4}` | `{"roomId": "R123", "roomName": "...", "hostId": "..."}` | Tạo phòng chơi mới. |
| `POST` | `/rooms/{roomId}/join` | Có | Không | `{"success": true, "roomId": "R123"}` | Tham gia vào phòng chơi. |
| `POST` | `/rooms/{roomId}/leave` | Có | Không | `{"success": true}` | Rời khỏi phòng hiện tại. |
| `POST` | `/rooms/{roomId}/start` | Có (Host) | Không | `{"success": true, "status": "PLAYING"}` | Host bắt đầu ván đấu (tối thiểu 2 người). |

---

### 4.3. WebSocket & STOMP Protocol (Realtime Game Engine)

- **Handshake Endpoint**: `/ws` (kèm JWT qua header hoặc parameter handshake).
- **Subscribe Topic (Nhận dữ liệu từ server)**:
  - `/topic/room/{roomId}`: Nhận toàn bộ sự kiện và cập nhật ván chơi của phòng.
  - `/queue/player/{userId}/errors`: Nhận thông báo lỗi riêng tư của người chơi.
- **Application Destination Prefix**: `/app` (Client gửi lệnh lên server).

#### Các Lệnh Người Chơi Gửi (Client Destination: `/app/...`)
| Destination | Payload gửi lên | Điều kiện hợp lệ | Mô tả |
| :--- | :--- | :--- | :--- |
| `/app/room/{roomId}/roll-dice` | `{"turnId": 12}` | Đúng lượt (`currentTurn`), phase = `AWAITING_ROLL` | Yêu cầu tung xúc xắc. |
| `/app/room/{roomId}/buy-property` | `{"turnId": 12, "tileIndex": 5}` | Đúng lượt, phase = `AWAITING_DECISION`, đủ tiền | Mua ô đất vừa đáp chân xuống. |
| `/app/room/{roomId}/end-turn` | `{"turnId": 12}` | Đúng lượt, các hành vi bắt buộc đã xử lý xong | Kết thúc lượt và chuyển cho người kế tiếp. |
| `/app/room/{roomId}/pay-bail` | `{"turnId": 12}` | Đúng lượt, đang ở tù, đủ tiền đóng bảo lãnh | Nộp tiền phạt $50 để ra tù ngay. |
| `/app/room/{roomId}/use-card` | `{"turnId": 12, "cardId": "..."}` | Đúng lượt, sở hữu thẻ phù hợp (Thẻ ra tù...) | Sử dụng thẻ cơ hội/khí vận. |

#### Các Sự kiện Server Phát (Broadcast Events qua `/topic/room/{roomId}`)
Mọi event trả về đều có định dạng chuẩn:
```json
{
  "eventType": "DICE_ROLLED",
  "turnId": 12,
  "timestamp": 1773300800000,
  "data": { ... }
}
```

| Event Type | Dữ liệu `data` tiêu biểu | Mô tả |
| :--- | :--- | :--- |
| `PLAYER_JOINED` | `{"player": {"id": "...", "name": "..."}}` | Có người chơi mới vào phòng. |
| `GAME_STARTED` | `{"board": [...], "players": [...]}` | Ván đấu bắt đầu, bàn cờ được khởi tạo. |
| `TURN_START_TIMER` | `{"playerId": "P1", "turnId": 12, "timeoutSeconds": 30}` | Bắt đầu đếm ngược lượt của người chơi. |
| `DICE_ROLLED` | `{"playerId": "P1", "dice1": 3, "dice2": 4, "isDouble": false, "newPosition": 7}` | Server sinh xúc xắc và cập nhật vị trí. |
| `PROPERTY_BOUGHT` | `{"playerId": "P1", "tileIndex": 7, "price": 100, "remainingMoney": 1400}` | Người chơi mua đất thành công. |
| `RENT_PAID` | `{"payerId": "P1", "ownerId": "P2", "amount": 50}` | Người chơi trả tiền thuê khi đáp vào đất người khác. |
| `PLAYER_IN_JAIL` | `{"playerId": "P1", "reason": "GO_TO_JAIL"}` | Người chơi bị đưa vào tù. |
| `PLAYER_BANKRUPT` | `{"playerId": "P1", "transferredTo": "P2"}` | Người chơi bị phá sản, tịch thu tài sản. |
| `GAME_OVER` | `{"winnerId": "P2", "standings": [...]}` | Ván đấu kết thúc, xác định người chiến thắng. |

---

## 5. Quy tắc Vàng Cho Người Tiếp Tục Code
1. **Server Authoritative Tuyệt đối**: Client chỉ là màn hình render dữ liệu. Tuyệt đối không nhận bất kỳ giá trị vị trí, số tiền, hay xúc xắc nào từ client gửi lên.
2. **Xử lý Độc lập theo GameRoom**: Mọi thao tác đồng thời (Concurrency) phải được serialize trong từng `GameRoom` (thông qua lock hoặc actor message), không block phòng khác.
3. **Quản lý Turn ID**: Mọi command phải gửi kèm `turnId`. Nếu `turnId` cũ hoặc sai lượt thì từ chối ngay lập tức để chống duplicate/stale command.
4. **Không expose Entity ra API**: Luôn map Domain Entity sang Request/Response DTO trước khi trả về REST hoặc WebSocket.
