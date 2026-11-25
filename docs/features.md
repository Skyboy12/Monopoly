# Tài liệu Tính năng Hiện tại — Monopoly Online

Tài liệu này mô tả các tính năng đã hoàn thiện và đang hoạt động trong hệ thống **Monopoly Online**.

---

## 1. Lối chơi Cờ Tỷ Phú Chuẩn (Core Gameplay)
- **Bàn cờ 40 ô đa hình**: Bao gồm ô Bắt đầu (`GO` +$200,000 khi đi qua), Đất ở (`StreetProperty` chia theo nhóm màu, hỗ trợ xây nhà & khách sạn), Nhà ga (`RailroadProperty`), Công ty Tiện ích (`UtilityProperty`), Ô Thuế (`TaxTile`), Ô Thẻ Cơ hội / Khí vận (`CardTile`), Ô Vào Tù (`GoToJailTile`), Ô Thăm Tù (`JailTile`), và Bãi đỗ xe (`FreeParkingTile`).
- **Authoritative Server (`GameEngine`)**:
  - Xúc xắc (`Dice`) được sinh bằng `SecureRandom` trên server.
  - Quản lý chặt chẽ pha lượt (`TurnPhase`: `AWAITING_ROLL`, `RESOLVING_ACTION`, `AWAITING_DECISION`, `FINISHED`).
  - Hỗ trợ gieo đôi đi tiếp, gieo đôi 3 lần liên tiếp vào tù, thế chấp/chuộc đất, phá sản và tự động chuyển lượt khi hết giờ (`TurnTimerService`).

---

## 2. Hệ thống Vật phẩm (Items & Inventory)
Mỗi người chơi sở hữu túi đồ `Inventory` (sức chứa mặc định 5 ô) với các vật phẩm kích hoạt theo pha:
- **Xúc xắc Thần kỳ (`MagicDiceItem` - `PRE_ROLL`)**: Cho phép chọn trước tổng số bước di chuyển từ 2 đến 12; `GameEngine.performRoll()` tự động tiêu thụ `forcedDiceValue` để tạo kết quả xúc xắc khớp với giá trị đã chọn.
- **Khiên Hộ mệnh (`RentShieldItem` - `ON_LAND`)**: Tự động kích hoạt khi người chơi đáp xuống ô đất của đối thủ (chưa thế chấp), miễn 100% tiền thuê lượt đó và tiêu hao vật phẩm.
- **Cưỡng chế Thu mua (`HostileTakeoverItem` - `AWAITING_DECISION`)**: Trả tiền niêm yết cộng giá trị công trình để mua đứt một ô đất của đối thủ.
- **Thuốc nổ (`DynamiteItem` - `AWAITING_DECISION`)**: Phá hủy công trình trên ô đất mục tiêu.
- **Hoán đổi Vận mệnh (`FortuneSwapItem` - `AWAITING_DECISION`)**: Hoán đổi vị trí hoặc tài sản với người chơi khác.
- **Lệnh Truy nã (`ArrestWarrantItem` - `AWAITING_DECISION`)**: Đưa đối thủ vào tù.

---

## 3. Hệ thống Sự kiện Đặc biệt (Party Mode Events)
`GameEventManager` được tích hợp trực tiếp vào `GameRoom` và cập nhật vòng đời theo từng lượt trong `GameEngine.advanceTurn()` (`onTurnStart`):
- **Thuế Nhà Giàu Siêu Cấp (`RichTaxEvent`)**: Người giàu nhất bị trừ 40% tiền mặt chia đều cho các người chơi còn lại.
- **Cơn Điên Bất Động Sản (`PropertyBoomEvent`)**: Nhân 3 lần tiền thuê toàn bàn cờ trong thời gian hiệu lực.
- **Đảo Lộn Đất Đai (`ContinentalDriftEvent`)**: Tráo đổi quyền sở hữu 2 ô đất ngẫu nhiên giữa 2 người chơi.
- **Ngày Phát Tiền Cứu Trợ (`StimulusDayEvent`)**: Ngân hàng phát $500,000 cho mỗi người chơi.
- **Động Đất Cục Bộ (`EarthquakeEvent`)**: Làm sập 1 cấp nhà trên một khu vực ngẫu nhiên.
- **Mưa Bão Đóng Băng (`FrozenTileEvent`)**: Đóng băng 3 ô liên tiếp trong 3 lượt; người chơi đáp vào được miễn tiền thuê và tự động trượt thêm 2 bước.

---

## 4. Bot AI & Chơi Đa Người Chơi (Multiplayer)
- **Bot AI (`engine/bot`: `BotController`, `BotStrategy`, `DefaultBotStrategy`)**: Tự động đánh giá và thực hiện tung xúc xắc, nộp bảo lãnh, mua đất, nâng cấp nhà/khách sạn khi đến lượt hoặc khi người chơi bị ngắt kết nối/hết giờ.
- **WebSocket STOMP & REST API**: Đồng bộ trạng thái phòng chơi thời gian thực qua `GameWebSocketController` và `RoomRestController`.
- **Lưu trữ & Bảo mật**: Kết nối MySQL (`Connector`, `UserRepository`) lưu thông tin người dùng, kết hợp `cloudflare-firewall` bảo vệ endpoint.
