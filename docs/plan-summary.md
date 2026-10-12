# Tóm tắt kế hoạch Webgame Cờ Tỷ Phú

## Mục tiêu

Xây dựng webgame Cờ Tỷ Phú nhiều người chơi từ 2 đến 4 người theo mô hình client-server authoritative. Backend Java/Spring Boot là nguồn sự thật duy nhất về luật chơi, xúc xắc, tiền, tài sản và trạng thái ván. Frontend React/Vite chỉ gửi command và hiển thị snapshot hoặc event từ server.

## Phạm vi MVP

- Bàn cờ 40 ô, tạo và tham gia phòng, bắt đầu ván.
- Gieo xúc xắc, di chuyển, mua đất, trả tiền thuê, thuế và nhà tù.
- Phá sản, xác định người thắng, timeout, AFK và bot cơ bản.
- Chưa làm trao đổi tài sản nâng cao, đấu giá nâng cao, khôi phục ván sau restart, rating hoàn chỉnh và hiệu ứng giao diện nâng cao.

## Kiến trúc chính

- Backend: Java 17+, Spring Boot 3, REST, WebSocket/STOMP, Spring Security, JWT, BCrypt và PostgreSQL.
- Frontend: React, Vite, WebSocket/STOMP.
- Tầng Edge & Tường lửa: Cloudflare Worker đóng vai trò Edge Reverse Proxy & Firewall, drop ngay các request rác, path scanner (`.env`, `.php`, scanners), sai method, payload quá khổ (> 100KB) và spam rate limit trước khi chạm vào backend.
- Origin Shielding: Spring Boot chỉ chấp nhận request mang header bí mật `X-Origin-Verify` từ Cloudflare Worker, chống bypass trực tiếp vào IP gốc.
- Database chỉ lưu tài khoản, display name và rating points; database chỉ mở trong mạng LAN/local.
- Game state của phòng đang chơi lưu trong RAM bằng `GameRoom`.
- Mỗi `GameRoom` serialize command riêng; không khóa toàn hệ thống.

## Domain OOP

- `GameRoom` quản lý người chơi, bàn cờ, lượt, phase, turn ID và timer.
- `Player` quản lý tiền, vị trí, tài sản, nhà tù, AFK và phá sản.
- `Board` chứa các `Tile`; `Dice` dùng `SecureRandom`.
- `Tile` là abstract class, các lớp con xử lý hành vi bằng polymorphism: property, railroad, utility, tax, go, jail, go to jail và free parking.
- Domain state được đóng gói; controller không sửa trực tiếp tiền, vị trí hoặc tài sản.

## Luồng authoritative

1. Server xác thực phòng, người chơi, `turnId`, phase và quyền.
2. Server tự sinh xúc xắc.
3. Server di chuyển người chơi và gọi `onLand(...)` của tile.
4. Server xử lý giao dịch, nhà tù, mortgage, phá sản và action bắt buộc.
5. Server tạo kết quả bất biến rồi broadcast event hoặc snapshot.

## Timeout và bot

Timer được broadcast khi bắt đầu lượt. Ngay lần timeout đầu tiên, người chơi được chuyển sang `BotController` hoặc `BotStrategy` để xử lý lượt hiện tại. Các lượt sau vẫn khởi động timer và chỉ chuyển sang xử lý tự động khi lượt đó timeout. Người chơi có thể bật auto thủ công và giành lại quyền bằng action hợp lệ.

## Tiêu chí nghiệm thu

- Server là nguồn sự thật duy nhất; client không tự thay đổi tiền, vị trí, xúc xắc hoặc chủ sở hữu.
- Action sai người, sai lượt, sai phase, sai `turnId` hoặc gửi trùng đều bị từ chối.
- Tường lửa Cloudflare Worker drop triệt để request độc hại/rác tại Edge, backend Spring Boot chỉ nhận request hợp lệ từ Worker.
- Nhiều client nhận state nhất quán.
- Timeout, bot và reconnect không làm treo vòng chơi.
- Có unit test, integration test và concurrency test cho các luồng quan trọng.

## Phân công

| Thành viên | Phạm vi chính | Tài liệu chi tiết |
|---|---|---|
| Thành viên 1 | Phân tích, UML và hợp đồng API | [member-1-analysis-design.md](member-1-analysis-design.md) |
| Thành viên 2 | Core domain OOP và luật bàn cờ | [member-2-core-domain.md](member-2-core-domain.md) |
| Thành viên 3 | Game engine, state machine, timeout và bot | [member-3-game-engine.md](member-3-game-engine.md) |
| Thành viên 4 | Backend REST, WebSocket, bảo mật và persistence | [member-4-backend-platform.md](member-4-backend-platform.md) |
| Thành viên 5 | Frontend, kiểm thử tích hợp và triển khai | [member-5-frontend-testing.md](member-5-frontend-testing.md) |

## Tài liệu mở rộng

- [Kế hoạch triển khai Hệ thống Vật phẩm và Sự kiện Đặc biệt (Item & Event System)](event-and-item.md)
- [Kế hoạch thiết kế và triển khai Tường lửa Cloudflare Worker (Edge Firewall)](cloudflare-worker-firewall.md)

