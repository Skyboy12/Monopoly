# 🎲 Monopoly Party - Authoritative Multiplayer Webgame

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![Cloudflare](https://img.shields.io/badge/Cloudflare_Worker-Edge_Firewall-F38020?style=for-the-badge&logo=cloudflare&logoColor=white)](https://workers.cloudflare.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)

> **Dự án Bài tập lớn Lập trình Hướng đối tượng (OOP) - PTIT**  
> Trò chơi Cờ Tỷ Phú nhiều người chơi trực tuyến (2 - 4 người) áp dụng mô hình **Server-Authoritative**, tích hợp **Hệ thống Vật phẩm & Sự kiện Đặc biệt (Party Mode)** cùng **Tường lửa Edge Cloudflare Worker** bảo vệ máy chủ.

---

## 📖 Mục lục

- [1. Giới thiệu & Điểm nổi bật](#1-giới-thiệu--điểm-nổi-bật)
- [2. Kiến trúc Hệ thống & Luồng Mạng](#2-kiến-trúc-hệ-thống--luồng-mạng)
- [3. Thiết kế Hướng đối tượng (4 Trụ cột OOP & Design Patterns)](#3-thiết-kế-hướng-đối-tượng-4-trụ-cột-oop--design-patterns)
- [4. Hệ thống Tính năng Cốt lõi](#4-hệ-thống-tính-năng-cốt-lõi)
  - [4.1. Luật Bàn cờ Monopoly Tiêu chuẩn](#41-luật-bàn-cờ-monopoly-tiêu-chuẩn)
  - [4.2. Hệ thống Vật phẩm Party Mode](#42-hệ-thống-vật-phẩm-party-mode)
  - [4.3. Hệ thống Sự kiện Đặc biệt](#43-hệ-thống-sự-kiện-đặc-biệt)
  - [4.4. Bot AI & Chống Treo ván (Anti-Deadlock)](#44-bot-ai--chống-treo-ván-anti-deadlock)
- [5. Tường lửa Edge & Bảo mật Hệ thống](#5-tường-lửa-edge--bảo-mật-hệ-thống)
- [6. Cấu trúc Thư mục Dự án](#6-cấu-trúc-thư-mục-dự-án)
- [7. Danh mục API & WebSocket Protocol](#7-danh-mục-api--websocket-protocol)
- [8. Hướng dẫn Khởi chạy & Triển khai](#8-hướng-dẫn-khởi-chạy--triển-khai)
- [9. Kết quả Kiểm thử (Testing)](#9-kết-quả-kiểm-thử-testing)
- [10. Phân công Thành viên](#10-phân-công-thành-viên)

---

## 1. Giới thiệu & Điểm nổi bật

**Monopoly Party** giải quyết triệt để các hạn chế của những tựa game cờ tỷ phú truyền thống: ván đấu kéo dài quá lâu gây nhàm chán hoặc sự mất cân bằng giữa người dẫn đầu và người đi sau. Dự án kết hợp luật chơi kinh điển với phong cách **Chaotic Party Game** giúp tăng tính kịch tính, lật kèo bất ngờ và đem lại tiếng cười giữa bạn bè.

### ✨ Điểm nhấn kỹ thuật
- **Authoritative Server tuyệt đối**: Client chỉ là màn hình hiển thị (Dumb Terminal). Toàn bộ xúc xắc (`SecureRandom`), di chuyển, giao dịch tiền bạc, tài sản và vật phẩm đều do Backend kiểm soát 100%. Không có bất kỳ lỗ hổng nào cho phép Client tự ý đổi tiền hay dịch chuyển vị trí.
- **Vận dụng thuần thục 4 tính chất OOP**: Không lạm dụng `if-else / switch-case`, phân cấp đa hình ô cờ sạch sẽ, đóng gói an toàn và phân tách rõ ràng trách nhiệm giữa Domain Model và Web Framework.
- **Tường lửa biên Cloudflare Worker (Edge Firewall)**: Loại bỏ 100% request rác, path scanner (`.env`, `.php`, scanners), spam brute-force và payload ngoại cỡ tại mạng biên Cloudflare trước khi chạm vào máy chủ.
- **Origin Shielding**: Backend chỉ chấp nhận kết nối qua secret token header `X-Origin-Verify`, bảo vệ IP thật khi mở ra Internet qua Cloudflare Tunnel.
- **Phòng chống treo ván (Zero Game-Stall)**: Bộ đếm thời gian turn timer độc lập theo phòng kết hợp cùng Bot AI tự động tiếp quản khi người chơi mất kết nối hoặc timeout.

---

## 2. Kiến trúc Hệ thống & Luồng Mạng

Hệ thống được thiết kế theo kiến trúc phân tầng nhiều lớp từ Cloud Edge tới Database:

```
[ Web Browser (React + Vite) ]
             │  (HTTPS / WSS)
             ▼
[ Cloudflare Edge Network (300+ PoPs) ]
             │
      ┌──────┴──────────────────────────────────────────┐
      │ Cloudflare Worker (Edge Firewall)               │
      │ - Scanner & Bad Path Drop (.php, .env, ../)     │
      │ - HTTP Method Whitelist (GET, POST, OPTIONS)    │
      │ - Content-Length Protection (<= 100KB)          │
      │ - Pre-validate JWT & Rate Limiting              │
      │ - Inject Header bí mật: X-Origin-Verify         │
      └──────┬──────────────────────────────────────────┘
             │  (Cloudflare Tunnel / HTTPS Proxy)
             ▼
[ Origin Server (Spring Boot 3 + Java 17) ]
      ├── CloudflareOriginFilter (Reject Direct IP Access)
      ├── Spring Security & JWT Filter
      ├── REST Controllers (Lobby, Auth, Profile)
      ├── WebSocket STOMP Engine (/app, /topic, /queue)
      │      ▼
      ├── GameEngine & State Machine (Authoritative)
      │      ▼
      ├── Domain Model (GameRoom, Player, Board, Tiles, Items)
      │      ▼
      └── RAM-based Concurrent Session Pool
             │
[ PostgreSQL Database (Local/LAN only) ]
      └── Lưu trữ thông tin tài khoản, mật khẩu (BCrypt), Rating points
```

---

## 3. Thiết kế Hướng đối tượng (4 Trụ cột OOP & Design Patterns)

Dự án thể hiện trọn vẹn 4 tính chất OOP cốt lõi:

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
| (Đất, Ga, Nước/Điện)| (Thuế cố định/%|  |  (Thăm / Ở tù)  |  |  (Ô di chuyển)  |
+-------------------+  +----------------+  +-----------------+  +-----------------+
```

### 1. Trừu tượng (Abstraction)
- **`Tile`**: Lớp trừu tượng đại diện cho một ô cờ bất kỳ trên bàn cờ, định nghĩa hành vi chung qua phương thức trừu tượng `onLand(Player player, GameRoom room)`.
- **`Item`**: Lớp trừu tượng định nghĩa các hành vi dùng đồ: `canUse(...)` và `applyEffect(...)`.
- **`GameEvent`**: Khung xử lý sự kiện đặc biệt qua `onTrigger(room)` và `onExpire(room)`.

### 2. Kế thừa (Inheritance)
- Phân cấp ô cờ: `Tile` ➔ `PropertyTile` ➔ (`StreetProperty`, `RailroadProperty`, `UtilityProperty`).
- Phân cấp chức năng: `TaxTile`, `GoTile`, `JailTile`, `GoToJailTile`, `FreeParkingTile`.
- Các lớp con kế thừa toàn bộ thuộc tính cơ sở (vị trí, tên ô, giá bán) và tái sử dụng mã nguồn hiệu quả.

### 3. Đa hình (Polymorphism)
- Game Engine khi xử lý di chuyển **tuyệt đối không dùng `if-else / switch-case`**, chỉ gọi duy nhất:
  ```java
  Tile tile = room.getBoard().getTile(newPosition);
  tile.onLand(player, room); // Tự động kích hoạt hành vi tương ứng theo từng loại ô cờ
  ```
- Tính tiền thuê đa hình: `property.calculateRent(room)` tự động chuyển tiếp tới công thức của từng loại:
  - `StreetProperty`: Nhân theo số nhà / khách sạn.
  - `RailroadProperty`: Lũy tiến theo số ga cùng sở hữu ($25 \times 2^{n-1}$).
  - `UtilityProperty`: Nhân hệ số theo giá trị nút xúc xắc vừa gieo (`diceTotal * multiplier`).

### 4. Đóng gói (Encapsulation)
- Toàn bộ thuộc tính trong `Player`, `GameRoom`, `Board`, `Inventory` đều là `private`.
- Nghiêm cấm gán trực tiếp: `player.money = 1000`. Mọi thay đổi đều phải thông qua các phương thức nghiệp vụ có kiểm tra ràng buộc:
  - `addMoney(amount)`
  - `deductMoney(amount)`
  - `pay(amount, receiver)`
  - `buyProperty(property)`
  - `declareBankruptcy(creditor)`

### 🛠️ Design Patterns áp dụng
- **Aggregate Root Pattern**: `GameRoom` điều phối toàn bộ vòng đời, xoay lượt và bảo toàn tính toàn vẹn của ván cờ.
- **Observer Pattern**: `GameEventManager` theo dõi các mốc vòng đời để đếm ngược và thanh lý sự kiện hết hạn.
- **Decorator Pattern**: Biến đổi linh hoạt công thức tính tiền thuê khi chịu ảnh hưởng bởi sự kiện (Cơn sốt đất x3).
- **Strategy Pattern**: `BotController` định hình chiến thuật ra quyết định cho bot AI và xử lý AFK.

---

## 4. Hệ thống Tính năng Cốt lõi

### 4.1. Luật Bàn cờ Monopoly Tiêu chuẩn
- Bàn cờ 40 ô khép kín (index 0 ➔ 39).
- Xúc xắc 2 viên ngẫu nhiên bảo mật (`SecureRandom`). Đổ đôi được gieo tiếp, đổ đôi 3 lần liên tiếp bị vào tù.
- Mua đất, xây nhà, khách sạn, đóng thuế, thăm tù, đi tù và nhận tiền thưởng qua ô GO ($200).
- Cơ chế giải quyết nợ và tuyên bố phá sản (`BANKRUPT`), chuyển nhượng toàn bộ tài sản cho chủ nợ.

### 4.2. Hệ thống Vật phẩm Party Mode
Người chơi thu thập vật phẩm qua ô May Mắn, thưởng qua ô GO hoặc cứu trợ:

| Tên Vật phẩm | Trigger Phase | Tác động | Mục đích trải nghiệm |
|---|---|---|---|
| 🎲 **Xúc xắc Thần kỳ (Magic Dice)** | `PRE_ROLL` | Bản thân | Chọn chính xác số bước (2 - 12), né ô nguy hiểm |
| 🛡️ **Khiên Hộ mệnh (Rent Shield)** | `ON_LAND` | Bản thân | Miễn 100% tiền thuê khi dẫm vào khách sạn đối thủ |
| 💼 **Cưỡng chế Thu mua (Hostile Takeover)** | `AWAITING_DECISION` | Đối thủ | Trả tiền mua đứt 1 ô đất bất kỳ của đối thủ |
| 🧨 **Thùng Thuốc nổ (Dynamite)** | `AWAITING_DECISION` | Ô đất | Thổi bay toàn bộ nhà/khách sạn trên 1 ô đất chỉ định |
| 🚔 **Lệnh Bắt Khẩn cấp (Arrest Warrant)** | `AWAITING_DECISION` | Đối thủ | Tống thẳng 1 người chơi bất kỳ vào tù ngay lập tức |
| 🔄 **Gậy Đổi Vận (Fortune Swap)** | `AWAITING_DECISION` | Đối thủ | Hoán đổi vị trí bàn cờ với người chơi giàu nhất bàn |

### 4.3. Hệ thống Sự kiện Đặc biệt
- **Sự kiện Toàn cục (Global Events)**:
  - *Thuế Nhà Giàu Siêu Cấp*: Trừ 40% tiền mặt của người giàu nhất chia đều cho những người khác.
  - *Cơn Điên Bất Động Sản*: Nhân 3 lần tiền thuê toàn bộ ô đất trong 3 lượt tiếp theo.
  - *Đảo Lộn Đất Đai*: Tráo đổi ngẫu nhiên quyền sở hữu 2 ô đất giữa 2 người chơi.
  - *Ngày Phát Tiền Cứu Trợ*: Ngân hàng tặng mỗi người chơi $500.
- **Sự kiện Cục bộ (Tile Events)**:
  - *Động Đất Cục Bộ*: Sập 1 cấp nhà trên một khu phố ngẫu nhiên.
  - *Mưa Bão Đóng Băng*: Đóng băng 3 ô cờ liên tiếp, không mất tiền thuê nhưng trượt thêm 2 bước.

### 4.4. Bot AI & Chống Treo ván (Anti-Deadlock)
- Mỗi lượt có bộ đếm ngược 30 giây (`TurnTimerService`).
- Khi hết giờ (Timeout) hoặc người chơi chủ động bật auto: `BotController` tự động gieo xúc xắc, mua đất nếu số dư dồi dào ($\ge 3$ lần giá đất) và chuyển lượt.
- Phase phản ứng khi dẫm vào ô đất (dùng Khiên Hộ Mệnh) chỉ kéo dài 8 giây trước khi tự động khấu trừ tiền thuê.

---

## 5. Tường lửa Edge & Bảo mật Hệ thống

Hệ thống được bảo vệ 2 lớp khi public ra Internet:

### Lớp 1: Cloudflare Worker (Edge Firewall)
- **Scanner Path Drop**: Drop lập tức mã độc phổ biến (`.env`, `.php`, `wp-admin`, `phpmyadmin`, `../`).
- **HTTP Method Whitelist**: Chỉ cho phép `GET`, `POST`, `OPTIONS` (Upgrade WebSocket cho `GET`). Drop toàn bộ `PUT`, `DELETE`, `TRACE`, `PATCH`.
- **Giới hạn Payload**: Drop ngay các request `Content-Length > 100KB` (chống DoS / tràn bộ nhớ).
- **Pre-validation JWT**: Drop ngay tại Edge các request thiếu hoặc sai cấu trúc JWT trước khi chuyển tiếp.
- **Origin Header Injection**: Worker tự động gắn header bí mật `X-Origin-Verify: <SECRET_KEY>`.

### Lớp 2: Origin Shielding (Spring Boot)
- Bộ lọc `CloudflareOriginFilter` kiểm tra header bí mật trên mọi request.
- Nếu kẻ tấn công tìm ra IP gốc và gửi request trực tiếp không qua Cloudflare ➔ Drop ngay `HTTP 403 Forbidden`.

---

## 6. Cấu trúc Thư mục Dự án

```text
OOP_BTL/
├── docs/                                  # Tài liệu phân tích, UML và kiến trúc
│   ├── member-1-analysis-design.md
│   ├── member-2-core-domain.md
│   ├── member-3-game-engine.md
│   ├── member-4-backend-platform.md
│   ├── member-5-frontend-testing.md
│   ├── event-and-item.md
│   ├── cloudflare-worker-firewall.md
│   └── plan-summary.md
├── Monopoly/
│   └── src/                               # Toàn bộ mã nguồn Java
│       ├── Main.java                      # Entry point khởi chạy hệ thống
│       ├── model/                         # Core Domain Model (OOP)
│       │   ├── Board.java                 # Bàn cờ 40 ô
│       │   ├── Dice.java                  # Xúc xắc SecureRandom
│       │   ├── GameRoom.java              # Aggregate Root quản lý ván cờ
│       │   ├── Player.java                # Thực thể người chơi đóng gói
│       │   ├── PlayerStatus.java          # ACTIVE, IN_JAIL, BANKRUPT
│       │   ├── RoomStatus.java            # WAITING, PLAYING, FINISHED
│       │   ├── tile/                      # Phân cấp ô cờ đa hình
│       │   │   ├── Tile.java              # Abstract base tile: onLand(...)
│       │   │   ├── PropertyTile.java      # Ô sở hữu được: calculateRent(...)
│       │   │   ├── StreetProperty.java    # Đất thông thường
│       │   │   ├── RailroadProperty.java  # Nhà ga xe lửa
│       │   │   ├── UtilityProperty.java   # Công ty điện/nước
│       │   │   ├── TaxTile.java           # Ô đóng thuế
│       │   │   ├── GoTile.java            # Ô xuất phát
│       │   │   ├── JailTile.java          # Ô thăm tù / thụ án
│       │   │   ├── GoToJailTile.java      # Ô vào tù
│       │   │   └── FreeParkingTile.java   # Ô đỗ xe miễn phí
│       │   ├── item/                      # Hệ thống vật phẩm Party Mode
│       │   │   ├── Item.java              # Abstract item: canUse, applyEffect
│       │   │   ├── Inventory.java         # Túi đồ người chơi
│       │   │   ├── ItemTargetContext.java # Ngữ cảnh mục tiêu dùng đồ
│       │   │   └── ItemTriggerPhase.java  # PRE_ROLL, ON_LAND, AWAITING_DECISION
│       │   └── event/                     # Hệ thống sự kiện đặc biệt
│       │       ├── GameEvent.java         # Abstract event: onTrigger, onExpire
│       │       └── GameEventManager.java  # Quản lý vòng đời sự kiện (Observer)
│       ├── engine/                        # Game Engine & State Machine
│       │   ├── GameEngine.java            # Authoritative Turn Loop
│       │   ├── TurnPhase.java             # AWAITING_ROLL, RESOLVING, DECISION
│       │   └── bot/
│       │       └── BotController.java     # Bot AI tự động
│       ├── server/                        # Tầng mạng, bảo mật và dịch vụ
│       │   ├── controller/
│       │   │   ├── RoomRestController.java
│       │   │   └── GameWebSocketController.java
│       │   ├── security/
│       │   │   ├── CloudflareOriginFilter.java
│       │   │   └── JwtTokenProvider.java
│       │   └── service/
│       │       ├── RoomManagerService.java
│       │       └── TurnTimerService.java
│       ├── network/
│       │   └── GameMessage.java           # DTO trao đổi dữ liệu chuẩn
│       ├── client/                        # Mã nguồn giao diện / Client
│       └── test/                          # Bộ kiểm thử Non-Frontend (Headless)
│           └── NonFrontendTestRunner.java
├── cloudflare-firewall/                   # Cloudflare Worker Edge Firewall
│   ├── wrangler.toml
│   └── src/index.ts
├── plan.md                                # Kế hoạch chi tiết tổng thể
├── workflow.md                            # Quy trình Git & PR Guidelines
└── README.md                              # Tài liệu dự án
```

---

## 7. Danh mục API & WebSocket Protocol

### 7.1. REST API (`/api/v1`)
| Method | Endpoint | Yêu cầu Auth | Mô tả |
|---|---|:---:|---|
| `POST` | `/api/v1/auth/register` | Không | Đăng ký tài khoản (Mật khẩu hash BCrypt) |
| `POST` | `/api/v1/auth/login` | Không | Đăng nhập nhận JWT Token |
| `GET` | `/api/v1/users/me` | Có | Xem thông tin cá nhân & Rating points |
| `GET` | `/api/v1/rooms` | Có | Lấy danh sách các phòng chờ |
| `POST` | `/api/v1/rooms` | Có | Tạo phòng chơi mới |
| `POST` | `/api/v1/rooms/{id}/join` | Có | Tham gia vào phòng chơi |
| `POST` | `/api/v1/rooms/{id}/leave` | Có | Rời khỏi phòng hiện tại |
| `POST` | `/api/v1/rooms/{id}/start` | Có (Host) | Bắt đầu ván đấu (tối thiểu 2 người) |

### 7.2. WebSocket / STOMP (`/ws`)
- **App Prefix (`/app`)**:
  - `/app/room/{id}/roll-dice`: Lệnh gieo xúc xắc (kèm `turnId`).
  - `/app/room/{id}/buy-property`: Quyết định mua ô đất.
  - `/app/room/{id}/use-item`: Kích hoạt vật phẩm.
  - `/app/room/{id}/end-turn`: Kết thúc lượt.
- **Topic Subscribe (`/topic`)**:
  - `/topic/room/{id}`: Nhận toàn bộ snapshot và event ván đấu (`DICE_ROLLED`, `PROPERTY_BOUGHT`, `ITEM_USED`, `TURN_CHANGED`, `PLAYER_BANKRUPT`).
  - `/queue/player/{userId}/errors`: Kênh thông báo lỗi riêng tư.

---

## 8. Hướng dẫn Khởi chạy & Triển khai

### Yêu cầu môi trường
- **Java**: OpenJDK 17 trở lên.
- **Node.js**: v18+ (đối với Frontend và Cloudflare Worker).
- **PostgreSQL**: 15+ (cấu hình trong mạng local).

### Các bước khởi chạy

#### 1. Biên dịch và chạy Backend độc lập
```bash
# Biên dịch toàn bộ mã nguồn Java
javac -encoding UTF-8 -d "Monopoly/bin" $(find Monopoly/src -name "*.java")

# Khởi chạy ván cờ Authoritative Engine
java -Dfile.encoding=UTF-8 -cp "Monopoly/bin" Main
```

#### 2. Chạy bộ kiểm thử Non-Frontend
```bash
javac -encoding UTF-8 -d "Monopoly/bin" $(find Monopoly/src Monopoly/test -name "*.java")
java -Dfile.encoding=UTF-8 -cp "Monopoly/bin" test.NonFrontendTestRunner
```

#### 3. Triển khai Cloudflare Worker (Tường lửa Edge)
```bash
cd cloudflare-firewall
npm install
# Cấu hình SECRET_KEY và triển khai lên Edge
npx wrangler secret put ORIGIN_SECRET_KEY
npx wrangler deploy
```

---

## 9. Kết quả Kiểm thử (Testing)

Dự án cung cấp bộ kiểm thử không phụ thuộc giao diện (**Headless / Non-Frontend Test Suite**) xác thực 100% các ràng buộc logic:

```text
=========================================================
   MONOPOLY NON-FRONTEND TEST SUITE (HEADLESS TESTING)   
=========================================================
[1] Kiểm thử: Player Encapsulation & Giao dịch tiền    -> [PASS 8/8]
[2] Kiểm thử: Board 40 ô & Polymorphic Tiles          -> [PASS 6/6]
[3] Kiểm thử: Tiền thuê Ga (RailroadProperty)         -> [PASS 2/2]
[4] Kiểm thử: Tiền thuê Điện Nước (UtilityProperty)   -> [PASS 1/1]
[5] Kiểm thử: GoToJailTile & TaxTile                  -> [PASS 3/3]
[6] Kiểm thử: Authoritative Engine & Chống gian lận    -> [PASS 8/8]
[7] Kiểm thử: Phá sản & Bàn giao tài sản              -> [PASS 6/6]
[8] Kiểm thử: Bot Controller tự động hoàn thành lượt   -> [PASS 3/3]
[9] Kiểm thử: Item & Inventory System                 -> [PASS 6/6]
---------------------------------------------------------
KẾT QUẢ KIỂM THỬ:
 -> ĐẠT (PASSED): 43 / 43
 -> THẤT BẠI (FAILED): 0
=========================================================
TẤT CẢ TEST CASES ĐÃ ĐẠT CHUẨN AUTHORITATIVE & OOP!
```

---

## 10. Phân công Thành viên

| Thành viên | Trách nhiệm chính | Tài liệu đặc tả |
|---|---|---|
| **Thành viên 1** | Phân tích yêu cầu, thiết kế UML Class/Sequence Diagram, đặc tả hợp đồng API | [member-1-analysis-design.md](docs/member-1-analysis-design.md) |
| **Thành viên 2** | Triển khai Core Domain OOP: Player, Board, phân cấp Tile đa hình, giao dịch | [member-2-core-domain.md](docs/member-2-core-domain.md) |
| **Thành viên 3** | Xây dựng Game Engine, Turn State Machine, Turn Timer, Bot Strategy | [member-3-game-engine.md](docs/member-3-game-engine.md) |
| **Thành viên 4** | Xây dựng Backend REST, WebSocket STOMP, JWT Auth, Tường lửa Cloudflare | [member-4-backend-platform.md](docs/member-4-backend-platform.md) |
| **Thành viên 5** | Phát triển Frontend React, diễn hoạt bàn cờ, kiểm thử tích hợp toàn diện | [member-5-frontend-testing.md](docs/member-5-frontend-testing.md) |

---

*© 2026 Nhóm Bài tập lớn OOP - Học viện Công nghệ Bưu chính Viễn thông (PTIT)*

