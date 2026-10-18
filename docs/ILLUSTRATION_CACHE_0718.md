> Tài liệu lịch sử. Hướng dẫn hiện hành: [Pocket1.0](../README.md). Các mô tả Cloudflare/phiên bản cũ không áp dụng mặc định.

# Bản vá 0.7.18
- Xét cả tên tập/tên chương, chuẩn hóa dấu Việt, hỗ trợ Minh họa/Minh hoạ/Illustration/Fanart/Artbook; không dùng từ ảnh đơn độc.
- Chỉ ảnh có nội dung hợp lệ: xác nhận minh họa. Ảnh kèm tối đa 24 từ và nhãn minh họa: ứng viên, KHÔNG coi là đã đọc hoặc loại khỏi kiểm chứng Đọc hết. Mốc 24 là quy tắc gợi ý thận trọng, không khẳng định chuẩn chung của Hako.
- Minh họa cũ có khung HTML hợp lệ: lưu fingerprint khung, không tự thử lại ảnh khi đồng bộ. File hỏng/không có nội dung không được miễn kiểm tra chỉ vì tồn tại.
- Kiểm tra cache ở bước chọn chương và ngay trước request. Chữ hợp lệ không phụ thuộc ready. Không ghi fake ready cho ảnh thiếu.
- Chữ trước minh họa; lỗi minh họa được ghi nhận và tiếp tục lượt. Giữ cooldown nếu Hako thật sự giới hạn truy cập.
- Menu reader thêm Chỉ tải lại ảnh thiếu: dùng khung đã lưu, tái dùng ảnh tốt; chỉ lấy lại trang nếu thiếu thông tin nguồn.
- Giữ SQLite v3/package/chữ ký/cache/đọc full/phạm vi 15+giữ3/điều kiện sạc+Wi-Fi.
- Mẫu tên đối chiếu: Gã giáo sư (Minh họa · Ellie/Judith), Isekai Meikyuu (Minh họa các tập · Vol16), Bungaku Shoujo (minh họa 12 từ), Seishun, DanKatsu, Tôi kẻ số hai. Một số trang bị403, kết quả lập chỉ mục chưa chứng minh nội dungDOMđãgiảimã hoặc hành vi thật trênS4.
