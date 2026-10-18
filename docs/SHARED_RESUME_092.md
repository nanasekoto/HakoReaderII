# Pocket 0.9.2 — Đọc tiếp dùng chung

Nút Đọc tiếp trên trang chủ hỏi Ubuntu truyện vừa đọc mới nhất, nhập metadata/mục lục nếu máy chưa có, rồi mở neo đọc. Không tự chuyển nội dung khi đang đọc một chương. Vị trí chưa gửi vẫn được giữ và ưu tiên gửi trước khi nhận từ server.

Trang chủ có trạng thái vị trí riêng: Chờ gửi, Đang gửi, Đã gửi vị trí kèm giờ và tên truyện, hoặc Chờ kết nối. Chỉ thông báo đã gửi sau phản hồi chấp nhận của server cho đúng sự kiện mới nhất. Đây là xác nhận vị trí; dữ liệu thư viện/số chương đi sau.

Thông tin truyện chưa từng có trên Ubuntu được gửi tối thiểu cùng vị trí ưu tiên; mục lục đầy đủ theo sau không đổi ngược truyện Đọc tiếp. Yêu thích chưa đọc cũng gửi metadata mà không tạo vị trí đọc giả.

Gói đồng bộ gồm lịch sử/vị trí, ID chương đã đọc, yêu thích/hoàn thành và metadata dung lượng đã lưu. Máy nhận lưu snapshot để mở danh sách khi offline. Có thể hiện truyện chỉ được lưu trên server/máy khác; nhãn Nơi khác lưu khác với Máy này. Không tạo file chương hoặc đánh dấu ready từ metadata. Muốn đọc offline trên máy nhận vẫn cần tải nội dung thực.

Phân loại chương chữ/minh họa đã biết được chia sẻ; không tự gửi đánh dấu Đọc hết lên website chỉ từ số đếm nhận về. Tiến độ không có dịch vụ dự phòng khi Ubuntu tắt.

Kiểm tra: server 67 tests pass; thêm test Android nhận truyện chưa tồn tại, giữ neo, đếm chưa đọc, danh sách đã lưu dùng chung và xác nhận đúng sự kiện. CI cuối 39098735436 thành công; APK cài đè trên S4/K60, giữ dữ liệu. Không suy ra các phần chưa thử từ fixture.

Lượt thử vật lý đầu xác nhận chọn truyện mới giữa hai máy và trạng thái ACK, đồng thời phát hiện lịch sử cũ chưa được đưa lên và số đếm bị ảnh hưởng bởi chương ẩn. Đã thêm gói index nhập lịch sử cũ một lần (tối đa 8 truyện/gói), không đổi vị trí mới nhất. Snapshot đếm theo chương hiện hành; chương ẩn vẫn được giữ. Truyện chỉ có metadata nhận từ máy kia không tự được tải cho tới khi mở đọc/chủ động tải. Đã thử lại APK cuối trên cả hai thiết bị.


## Kết quả bản cuối

- App commit 907cb3645cff47ca4589114dd4cc92cf58e44fb6, 0.9.2/code65, CI 39098735436 thành công; Android PagedInteractionTest có 96 kiểm tra đạt.
- SHA256 APK: `4ddd716311ed706ed4100a216a4de870fa06b9dfccba1f8a20b407fdfa72f41a`; đã đối chiếu file cài trên hai thiết bị và file tải qua HTTPS.
- Thử thật: S4 đọc Gã Giáo Sư, K60 đọc Tà Thuật Sư/Chương 1 Mở hộp mù và rời chương; có ACK giờ/tên truyện. S4 chỉ bấm Đọc tiếp đã mở Tà Thuật Sư tại đoạn tương ứng. Neo cuộn K60 2/27 sang S4 5/57, có thể hiện thêm dòng trước neo.
- K60 nhận đủ 12 truyện thật trong lịch sử; S4 có thêm Hướng dẫn Hako Pocket offline, không gửi mục demo này lên server.
- Gã Giáo Sư hiển thị 0 chưa đọc ở cả hai máy sau loại chương ẩn khỏi số đếm hiện hành. Danh sách đã lưu khớp 3 bộ; Twintail S4 có 52/52, K60 có 0/52 và metadata nơi khác 52/52.
- Đã sửa riêng trên server việc khoảng trắng/xuống dòng khiến nút Đọc tiếp bị hiểu là tên chương; không thay APK vì lỗi thuộc trích xuất server.
- Trong một lần cập nhật server, Uvicorn cũ chờ đóng kết nối khiến HTTPS tạm 502; đã dừng tiến trình cũ, khởi động lại, HTTPS 200 và SQLite quick_check ok. Các mount SSD/HDD và cấu hình chỉ Ubuntu được giữ.
- Chưa đo pin/độ trễ định lượng; không kiểm tra toàn bộ website hoặc thao tác Hako mark-read thật trong lượt này.
