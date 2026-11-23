# Thành viên 5: Frontend, kiểm thử hệ thống và triển khai

## Mục tiêu

Xây dựng giao diện sử dụng được cho toàn bộ MVP, kiểm thử các luồng liên module và chuẩn bị tài liệu chạy hệ thống trong LAN hoặc Internet.

## Công việc frontend

1. Khởi tạo React/Vite và tạo màn hình login/register, lobby, room, bàn cờ và màn hình kết thúc.
2. Kết nối WebSocket/STOMP để nhận snapshot/event; client chỉ render state từ server.
3. Hiển thị người chơi, tiền, vị trí, tài sản, turn timer, phase, action controls và thông báo lỗi.
4. Khóa hoặc mở control dựa trên phase, actor và quyền server gửi; không tự tính xúc xắc, tiền thuê, tiền hoặc vị trí.
5. Xử lý reconnect, snapshot mới nhất, trạng thái loading/error và responsive cho desktop/mobile.

## Công việc kiểm thử và triển khai

1. Viết end-to-end test cho đăng nhập, tạo phòng, tham gia, bắt đầu ván, gieo xúc xắc, mua đất và kết thúc ván.
2. Kiểm thử nhiều client trong cùng phòng, reconnect, timeout, bot và action không hợp lệ.
3. Chạy concurrency test cùng thành viên 3 và integration test cùng thành viên 4.
4. Viết README setup, cấu hình môi trường, migration, giới hạn MVP và hướng dẫn chạy LAN.
5. Chuẩn bị cấu hình deploy, log/monitoring cơ bản, domain và SSL khi triển khai Internet.
6. Xây dựng Cloudflare Worker script (Tường lửa Edge) để drop các request rác, path scanner, malformed payload và spam rate-limit.
7. Cấu hình Cloudflare Tunnel kết nối Origin an toàn và sinh header `X-Origin-Verify`.
8. Kiểm thử "bắn" request dị dạng/scanner từ bên ngoài để xác nhận Edge Worker drop thành công trước khi request tới Spring Boot.
9. Chỉ mở port cần thiết; không port-forward database trực tiếp ra Internet.

## Sản phẩm bàn giao

- Frontend hoàn chỉnh cho các màn hình MVP.
- E2E test và báo cáo kết quả kiểm thử.
- README setup, migration, giới hạn và hướng dẫn triển khai.
- Checklist nghiệm thu theo tiêu chí trong `plan.md`.

## Phụ thuộc và phối hợp

- Nhận DTO, event và error code từ thành viên 4.
- Nhận state/timer/bot behavior từ thành viên 3.
- Đối chiếu giao diện với luật và contract do thành viên 1 chốt.

## Tiêu chí hoàn thành

- Người dùng hoàn thành được các luồng MVP từ đăng nhập đến kết thúc ván.
- Giao diện không cho gửi action khi không có quyền hoặc sai phase.
- Test hệ thống phát hiện được duplicate action, reconnect lỗi và state không nhất quán.
