# Thành viên 3: Game engine, state machine, timeout và bot

## Mục tiêu

Xây dựng vòng đời lượt chơi authoritative, xử lý command tuần tự trong từng phòng và bảo đảm timeout/bot không làm treo ván.

## Công việc

1. Implement state machine cho `WAITING`, `PLAYING`, `RESOLVING_ACTION`, `AWAITING_DECISION`, `TIMEOUT` và `FINISHED`.
2. Validate `roomId`, `playerId`, `turnId`, phase, quyền và duplicate action trước khi thay đổi state.
3. Implement flow gieo xúc xắc, di chuyển, gọi tile action, chờ quyết định và chuyển lượt.
4. Dùng `ScheduledExecutorService` hoặc Spring Scheduler cho turn timer; hủy timer cũ khi lượt kết thúc.
5. Khi timeout lần đầu, chuyển ngay người chơi sang `BotController` hoặc `BotStrategy` để xử lý lượt hiện tại.
6. Ở các lượt sau, vẫn khởi động timer và chỉ để bot xử lý khi lượt đó timeout; hỗ trợ người chơi giành lại quyền bằng action hợp lệ.
7. Implement bot tự gieo xúc xắc, mua đất khi có đủ tiền theo luật và xử lý action bắt buộc.
8. Tạo immutable action result/event và kiểm soát race condition giữa timeout, reconnect và command trùng.
9. Viết test cho state transition, timeout, bot, duplicate action và hai command đồng thời trong cùng phòng.

## Sản phẩm bàn giao

- Package `engine` và strategy/controller cho bot.
- Timer service và cơ chế serialize command theo `GameRoom`.
- Event/result immutable cho các thay đổi state.
- Unit test và concurrency test của engine.

## Phụ thuộc và phối hợp

- Dùng domain API của thành viên 2 và state contract của thành viên 1.
- Cung cấp event cần thiết cho WebSocket của thành viên 4 và timer state cho frontend của thành viên 5.

## Tiêu chí hoàn thành

- Không thể chuyển lượt hai lần hoặc xử lý một action cũ.
- Timeout đầu tiên kích hoạt bot đúng lượt; các lượt tiếp theo vẫn chờ timer.
- Nhiều command đồng thời không làm mất tiền, gán trùng tài sản hoặc sai lượt.
