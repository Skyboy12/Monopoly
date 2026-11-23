# Thành viên 1: Phân tích và thiết kế

## Mục tiêu

Chốt luật MVP, mô hình domain, state machine và hợp đồng giao tiếp để các thành viên khác triển khai cùng một thiết kế.

## Công việc

1. Xác định luật cho 40 ô: giá đất, tiền thuê, thuế, nhà tù, ô GO, free parking và điều kiện phá sản.
2. Vẽ UML class diagram thể hiện abstraction, inheritance, polymorphism và encapsulation.
3. Vẽ sequence diagram cho các luồng: tạo phòng, bắt đầu ván, gieo xúc xắc, mua đất, trả tiền thuê, timeout và reconnect.
4. Chốt enum/value object: `RoomStatus`, `TurnPhase`, `PlayerStatus`, `ActionType`, error code và transaction result.
5. Viết API contract cho REST và WebSocket: request, response, event, snapshot, validation error và quyền gửi command.
6. Ghi rõ invariant: tiền không âm trừ trường hợp xử lý phá sản, chỉ đúng người được hành động, mỗi action dùng một `turnId` hợp lệ.

## Sản phẩm bàn giao

- Tài liệu luật MVP.
- UML class diagram và sequence diagram.
- Bảng state transition của phòng và lượt chơi.
- Tài liệu REST/WebSocket contract.
- Danh sách error code và tiêu chí validation.

## Phụ thuộc và phối hợp

- Làm việc sớm với thành viên 2 và 3 để thống nhất domain API và state machine.
- Gửi contract cho thành viên 4 và 5 trước khi họ viết controller hoặc client.

## Tiêu chí hoàn thành

- Mọi command đều có phase, actor, quyền và kết quả rõ ràng.
- Không còn luật quan trọng chỉ nằm trong trao đổi miệng.
- Diagram phản ánh đúng các lớp và luồng được implement.
