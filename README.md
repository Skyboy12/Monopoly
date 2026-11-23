# Monopoly Online — Game Cờ Tỷ Phú Đa Người Chơi

Dự án Bài tập lớn Lập trình Hướng đối tượng (OOP - PTIT): Xây dựng hệ thống game **Cờ Tỷ Phú (Monopoly)** trực tuyến theo kiến trúc **Authoritative Server** (Java Backend + WebSocket STOMP + REST API) kết hợp giao diện **React Frontend** và lớp bảo vệ **Cloudflare Worker Firewall**.

---

## 1. Các đặc điểm Lập trình Hướng đối tượng (OOP) trong dự án

Dự án được thiết kế và cài đặt bám sát 4 tính chất cốt lõi của Lập trình Hướng đối tượng:

### 1.1. Tính Trừu tượng (Abstraction)
- **Hệ thống Ô cờ (`Tile` & `PropertyTile`)**: Lớp trừu tượng `Tile` định nghĩa khuôn mẫu chung cho mọi ô trên bàn cờ (chỉ số `index`, tên `name`, trạng thái đóng băng `frozen`, và phương thức trừu tượng `onLand(Player, GameRoom)`). Lớp `PropertyTile` kế thừa `Tile` và tiếp tục trừu tượng hóa `calculateRent(GameRoom)` để các loại tài sản cụ thể tự định nghĩa cách tính tiền thuê.
- **Hệ thống Sự kiện (`GameEvent`)**: Lớp trừu tượng `GameEvent` định nghĩa vòng đời của một sự kiện đặc biệt thông qua `onTrigger(GameRoom)` và `onExpire(GameRoom)`, giúp `GameEventManager` quản lý thời hạn hiệu lực mà không cần biết chi tiết bên trong từng sự kiện.
- **Hệ thống Vật phẩm (`Item`)**: Lớp trừu tượng `Item` quy định chuẩn `canUse(Player, GameRoom, ItemTargetContext)` và `applyEffect(Player, GameRoom, ItemTargetContext)` gắn với `ItemTriggerPhase` (`PRE_ROLL`, `ON_LAND`, `AWAITING_DECISION`).
- **Hệ thống Thẻ bài (`Card`) & Bot AI (`BotStrategy`)**: Tách biệt giao diện hành vi (`apply(Player, GameRoom)` cho thẻ Cơ hội/Khí vận, `decideAction(...)` cho chiến lược Bot) khỏi phần thực thi cụ thể.

### 1.2. Tính Kế thừa (Inheritance)
- **Phân cấp Ô cờ (`model.tile.*`)**:
  - `Tile` ➔ `GoTile`, `JailTile`, `GoToJailTile`, `TaxTile`, `CardTile`, `FreeParkingTile`, `PropertyTile`
  - `PropertyTile` ➔ `StreetTile` (đất có nhà/khách sạn), `RailroadTile` (nhà ga), `UtilityTile` (công ty điện/nước).
- **Phân cấp Sự kiện (`model.event.*`)**:
  - `GameEvent` ➔ `RichTaxEvent`, `PropertyBoomEvent`, `ContinentalDriftEvent`, `StimulusDayEvent`, `EarthquakeEvent`, `FrozenTileEvent`.
- **Phân cấp Vật phẩm (`model.item.*`)**:
  - `Item` ➔ `MagicDiceItem`, `RentShieldItem`, `DynamiteItem`, `FortuneSwapItem`, `ArrestWarrantItem`.

### 1.3. Tính Đa hình (Polymorphism)
- **Đa hình khi đáp ô (`tile.onLand(player, room)`)**: Trong `GameEngine.performRoll()`, engine chỉ cần gọi `tile.onLand(player, room)` trên tham chiếu `Tile` mà không cần `if/else` kiểm tra loại ô. Tùy vào đối tượng thực tế (`StreetTile`, `TaxTile`, `GoToJailTile`, `CardTile`,...), hành vi tương ứng sẽ được thực thi.
- **Đa hình khi tính tiền thuê (`calculateRent(room)`)**: `PropertyTile.onLand()` gọi `calculateRent(room)` — `StreetTile` tính theo số nhà/khách sạn, `RailroadTile` tính theo số nhà ga sở hữu, `UtilityTile` tính theo tổng xúc xắc.
- **Đa hình Sự kiện & Vật phẩm**: `GameEventManager` gọi `event.onTrigger(room)` và `event.onExpire(room)` thống nhất cho mọi `GameEvent`; hệ thống vật phẩm gọi `item.canUse(...)` và `item.applyEffect(...)` thống nhất cho mọi `Item`.

### 1.4. Tính Đóng gói (Encapsulation)
- **Aggregate Root `GameRoom`**: Toàn bộ trạng thái ván đấu (`players`, `board`, `turnId`, `status`, `forcedDiceValue`, `rentMultiplier`, `eventManager`) được đóng gói với `private` fields và đồng bộ hóa (`synchronized`) qua các phương thức điều phối như `nextTurn()`, `addPlayer()`.
- **Túi đồ `Inventory`**: Danh sách `items` được bảo vệ hoàn toàn bên trong `Inventory`, chỉ cho phép thao tác qua `addItem()`, `removeItem()`, `hasItem()`, và trả về `Collections.unmodifiableList(...)` ở `getItems()` để ngăn sửa đổi trực tiếp từ bên ngoài.
- **Người chơi `Player`**: Số dư `money`, trạng thái `status`, vị trí `position`, và khiên miễn thuê `rentShieldActive` chỉ thay đổi thông qua các nghiệp vụ hợp lệ (`pay()`, `receive()`, `move()`, `declareBankruptcy()`).

---

## 2. Cấu trúc thư mục

```text
OOP_BTL/
├── Monopoly/
│   ├── src/
│   │   ├── model/           # Domain Layer (GameRoom, Player, Board, Tile, Event, Item, Card)
│   │   ├── engine/          # GameEngine (State Machine, TurnPhase, ActionResult, EngineEvent)
│   │   ├── ai/              # BotController & BotStrategy
│   │   └── server/          # REST API, WebSocket STOMP Broker, Services, Database (MySQL)
│   ├── src/client/          # React Frontend (Vite)
│   ├── test/                # FullSystemTestRunner & Test Suites
│   └── lib/                 # Thư viện Java (MySQL Connector, v.v.)
├── cloudflare-firewall/     # Cloudflare Worker bảo vệ API & WebSocket
├── docs/
│   ├── features.md          # Tài liệu mô tả các tính năng hiện tại
│   └── development-docs/    # Tài liệu phân tích & thiết kế quá trình phát triển
└── README.md
```

---

## 3. Hướng dẫn biên dịch và chạy

### 3.1. Backend (Java)
Yêu cầu: **JDK 17+** (đã kiểm thử với JDK 25).
```powershell
javac -encoding UTF-8 -cp "Monopoly/lib/*" -d Monopoly/bin (Get-ChildItem -Recurse -Filter *.java Monopoly/src | ForEach-Object { $_.FullName })
java -cp "Monopoly/bin;Monopoly/lib/*" Main
```

### 3.2. Frontend (React)
```powershell
cd Monopoly/src/client
npm install
npm run dev
```

---

## 4. Tài liệu tham khảo
- [Tài liệu các tính năng hiện tại](docs/features.md)
- [Tài liệu phát triển (Development Docs)](docs/development-docs/plan-summary.md)
