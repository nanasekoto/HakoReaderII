> Tài liệu lịch sử. Hướng dẫn hiện hành: [Pocket1.0](../README.md). Các mô tả Cloudflare/phiên bản cũ không áp dụng mặc định.

# 0.7.17 Gecko
- Giữ package/chữ ký/SQLite v3 và các file cũ. Không xóa dữ liệu.
- Truyện đang đọc nhường lượt sau chương hiện tại; giữ hàng chờ cũ. Chương ưu tiên khác bộ không bị nuốt.
- Nội dung chỉ có ảnh được phân loại riêng. Tiêu đề chỉ gợi ý điều hướng, không đủ chứng cứ gửi Đọc hết.
- Tiến độ chữ tách chương minh họa; báo số ảnh thiếu riêng. Không gán ảnh lỗi thành đã đủ.
- Ảnh tải mới được nén JPEG 78, tối đa 480px ngang và 1600px dọc, giữ tỷ lệ. Ảnh cũ hợp lệ không bị tải lại hoặc ghi đè chỉ để nén.
- Minh họa từng thử tải không tự thử lại ở mỗi lần đồng bộ. Mở từ mục lục hoặc Tải lại chương này là thao tác chủ động.
- Đọc tiếp dùng vị trí thực tế, không suy ngược từ tổng unread. Chuyển chương bỏ qua minh họa. Mở ảnh không ghi đè vị trí đọc chữ.
- Đọc hết không tạo lịch sử đọc chữ giả. Minh họa chưa xem cần xác nhận riêng, lưu ID được chấp nhận trong preferences. Mục lục server mới/không rõ không được vượt qua kiểm chứng.
- Tủ sách hiển thị theo thứ tự nhận từ Hako; nhập từng trang giữ đuôi chưa quét với rank duy nhất. Không dùng trạng thái tải để sắp xếp lại tủ sách.
- Giữ nhịp nghỉ mạng và neo an toàn, chỉ bổ sung lý do chờ rõ ràng. Không hứa thời gian vài giây khi chưa đo.
- Hai URL minh chứng không truy cập được qua công cụ tra cứu (403). Kiểm tra tự động không thay thế thử nghiệm truyện thực tế trên S4.
