# Thành viên 2: Core domain và luật bàn cờ

## Mục tiêu

Implement mô hình OOP độc lập với framework web, chứa dữ liệu và nghiệp vụ cơ bản của người chơi, bàn cờ, ô cờ và giao dịch.

## Công việc

1. Implement `Player` với tiền, vị trí, tài sản, trạng thái nhà tù, AFK và phá sản; tất cả field phải private.
2. Implement `Board`, `Tile` và các subclass: `PropertyTile`, `StreetProperty`, `RailroadProperty`, `UtilityProperty`, `TaxTile`, `GoTile`, `JailTile`, `GoToJailTile`, `FreeParkingTile`.
3. Implement `GameRoom` ở mức domain: danh sách người chơi, trạng thái phòng, lượt hiện tại và turn ID.
4. Implement `Dice` bằng `SecureRandom`; không nhận kết quả xúc xắc từ client.
5. Đóng gói nghiệp vụ qua `addMoney`, `pay`, `buyProperty`, `transferProperty`, `declareBankruptcy` và các method tương đương.
6. Implement rent calculation, mortgage, mua đất, chuyển tài sản và các điều kiện giao dịch.
7. Viết unit test cho Player, giao dịch tiền, từng loại tile, dice và polymorphism của `onLand(...)`.

## Sản phẩm bàn giao

- Package `domain` và các value object liên quan.
- Unit test domain có dữ liệu biên: không đủ tiền, tài sản đã có chủ, phá sản, nhà tù và đi qua GO.
- Tài liệu ngắn mô tả invariant của domain.

## Phụ thuộc và phối hợp

- Nhận UML, luật và enum từ thành viên 1.
- Cung cấp API domain ổn định cho thành viên 3 tích hợp game engine.
- Không đưa annotation web hoặc logic controller vào core domain nếu không cần thiết.

## Tiêu chí hoàn thành

- Controller không thể sửa trực tiếp tiền, vị trí hoặc owner.
- Engine gọi được `board.getTile(position).onLand(player, room)` mà không kiểm tra type bằng `if-else`.
- Unit test kiểm chứng cả hành vi đúng và giao dịch bị từ chối.
