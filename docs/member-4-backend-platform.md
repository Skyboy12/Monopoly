# Thành viên 4: Backend, API, bảo mật và persistence

## Mục tiêu

Đưa domain và engine lên Spring Boot, cung cấp REST/WebSocket an toàn và lưu trữ đúng phần dữ liệu thuộc tài khoản.

## Công việc

1. Khởi tạo Spring Boot 3 với Java 17+, cấu trúc package `web`, `security`, `persistence`, `domain` và `engine`.
2. Implement REST API đăng ký, đăng nhập, thông tin cá nhân, tạo phòng, danh sách phòng, tham gia, rời phòng và bắt đầu ván.
3. Implement WebSocket/STOMP handshake, room topic, command endpoint và broadcast snapshot/event.
4. Tạo DTO request/response; không expose trực tiếp domain object ra API.
5. Implement JWT cho REST và WebSocket handshake, BCrypt cho password, phân quyền theo user, room và host.
6. Validate command ở boundary rồi ủy quyền xử lý cho game engine; không tin state do client gửi.
7. Xây dựng servlet filter (`CloudflareOriginFilter`) xác thực secret header `X-Origin-Verify` để từ chối các request bypass trực tiếp vào server.
8. Tạo migration/schema cho user ID, username, password hash, display name và rating points.
9. Viết integration test authentication, REST lobby, WebSocket command validation, broadcast snapshot và reconnect.
10. Bổ sung logging lỗi và xử lý exception nghiệp vụ thành error response thống nhất.

## Sản phẩm bàn giao

- Backend chạy được với cấu hình local.
- REST API và WebSocket endpoint theo contract.
- Security configuration, migration/schema và DTO.
- Integration test cho các luồng backend chính.

## Phụ thuộc và phối hợp

- Nhận API contract từ thành viên 1.
- Tích hợp engine của thành viên 3.
- Cung cấp endpoint, event và lỗi cho thành viên 5 xây frontend.

## Tiêu chí hoàn thành

- Client không thể tự gửi tiền, vị trí, dice result hoặc owner để ép server.
- Action sai user, room, phase hoặc turn ID trả về error đúng mã.
- Hai client cùng phòng nhận snapshot nhất quán.
