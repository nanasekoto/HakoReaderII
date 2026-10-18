# Pocket 0.9.2 — Đọc tiếp dùng chung

Nút Đọc tiếp trên trang chủ hỏi Ubuntu truyện vừa đọc mới nhất, nhập metadata/mục lục nếu máy chưa có, rồi mở neo đọc. Không tự chuyển nội dung khi đang đọc một chương. Vị trí chưa gửi vẫn được giữ và ưu tiên gửi trước khi nhận từ server.

Trang chủ có trạng thái vị trí riêng: Chờ gửi, Đang gửi, Đã gửi vị trí kèm giờ và tên truyện, hoặc Chờ kết nối. Chỉ thông báo đã gửi sau phản hồi chấp nhận của server cho đúng sự kiện mới nhất. Đây là xác nhận vị trí; dữ liệu thư viện/số chương đi sau.

Thông tin truyện chưa từng có trên Ubuntu được gửi tối thiểu cùng vị trí ưu tiên; mục lục đầy đủ theo sau không đổi ngược truyện Đọc tiếp. Yêu thích chưa đọc cũng gửi metadata mà không tạo vị trí đọc giả.

Gói đồng bộ gồm lịch sử/vị trí, ID chương đã đọc, yêu thích/hoàn thành và metadata dung lượng đã lưu. Máy nhận lưu snapshot để mở danh sách khi offline. Có thể hiện truyện chỉ được lưu trên server/máy khác; nhãn Nơi khác lưu khác với Máy này. Không tạo file chương hoặc đánh dấu ready từ metadata. Muốn đọc offline trên máy nhận vẫn cần tải nội dung thực.

Phân loại chương chữ/minh họa đã biết được chia sẻ; không tự gửi đánh dấu Đọc hết lên website chỉ từ số đếm nhận về. Tiến độ không có dịch vụ dự phòng khi Ubuntu tắt.

Kiểm tra: server 64 tests pass; thêm test Android nhận truyện chưa tồn tại, giữ neo, đếm chưa đọc, danh sách đã lưu dùng chung và xác nhận đúng sự kiện. Đang chờ CI và kiểm tra APK trên hai máy thật; không tuyên bố hoạt động toàn bộ từ fixture.
