# Bản chỉnh giao diện 1.0.2

Phạm vi: 9 ảnh S4 ngày 21/10/2026 do chủ sở hữu cung cấp.

- Tủ sách nhận thứ tự cập nhật từ Hako qua server, không dùng thời điểm ghi database để xếp kệ. Đồng bộ metadata thiếu shelf_rank giữ hạng đang có.
- Yêu thích: badge nền đen, chữ trắng, rộng theo nội dung, nằm bên phải dòng phụ.
- Vừa đọc: bỏ nhãn file lưu trên máy khác; tên truyện giãn theo số dòng thực, dòng chưa đọc và các nút thao tác nằm bên dưới.
- S4 khám phá: bỏ loại truyện lặp với bộ lọc, thu khoảng đệm, đặt chương mới cùng hàng các nút yêu thích/tải; giữ tên đầy đủ. Số truyện mỗi màn phụ thuộc độ dài tên/chương.
- S4 thông tin truyện: bỏ bộ chọn Tổng quan; gom Yêu thích/Mục lục/Tải chữ thành một hàng; thống kê ngay sau các nút trong phần đầu. Giới thiệu bắt đầu ở màn tiếp theo; nội dung quá dài tiếp tục theo dòng, không cắt hoặc thu nhỏ chữ để ép vừa.
- Bộ đếm nhỏ ở góc dưới thay hai nút lớn ở khám phá/chi tiết S4. Phím âm lượng chuyển màn; chạm bộ đếm có lựa chọn trước/sau. Khi hết một trang nguồn khám phá, volume chuyển trang nguồn kế tiếp.
- Giao diện khám phá/chi tiết K60 giữ bố cục cảm ứng và ảnh hiện hành.

Theo yêu cầu trực tiếp của chủ sở hữu: **chỉ đọc/sửa mã và build APK; không chạy test, giả lập hoặc thao tác thiết bị**. Build thành công chỉ xác nhận biên dịch/đóng gói; chủ sở hữu kiểm tra hiển thị thực tế. Không gọi đây là nghiệm thu UI đã đạt.

Workflow thủ công `apk-only.yml` chỉ assembleRelease và đóng gói APK. Workflow kiểm thử đầy đủ hiện hành được giữ cho các đợt khác.
