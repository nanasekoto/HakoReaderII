> Tài liệu lịch sử. Hướng dẫn hiện hành: [Pocket1.0](../README.md). Các mô tả Cloudflare/phiên bản cũ không áp dụng mặc định.

# Hako Pocket 0.7.21 — tiến độ theo mốc đọc, chờ gửi Hako và sắp xếp chương

Quy tắc mới do người dùng chốt ngày 12/10/2026, thay thế cách đếm từng chương và yêu cầu xác nhận minh họa của 0.7.20. Nền: 1e218b06cce33d07f7aebb4bf08456e7b0482c0f. VersionCode 37.

- Minh họa bỏ khỏi cả đã đọc và chưa đọc. Không hiện bộ đếm minh họa chưa xem hoặc yêu cầu đồng ý riêng. Ảnh thiếu vẫn được báo như tình trạng tải, không là tiến độ đọc.
- Tới trang cuối chương chữ, lưu ID làm mốc hoàn tất. Các chương chữ tới mốc đó mặc định đã đọc; phần phía sau chưa đọc. Mở một chương chưa tới trang cuối không nâng mốc.
- Tới chương chữ mới nhất tự lưu hàng chờ gửi Đã đọc lên Hako và hiện thông báo. Hàng chờ tồn tại khi offline và qua khởi động lại; tương thích hàng chờ cũ.
- Khi có mạng và kiểm tra tủ sách, lấy mục lục server mới. Chỉ gửi nếu ID chương chữ mới nhất vẫn bằng mốc đã hoàn tất và mốc của yêu cầu chờ. Có chương chữ mới hơn thì giữ hàng chờ và giữ phần mới chưa đọc. Minh họa mới không chặn thao tác này.
- Job gửi đã đọc dùng kết nối Internet bất kỳ; tải nền toàn thư viện vẫn theo lịch sạc + Wi-Fi. Job gửi kiểm tra tủ sách trước khi xử lý; cập nhật tủ sách thông thường cũng xử lý hàng chờ sau khi nhập metadata thành công.
- Tại cuối chương đang đọc, chuyển tiếp kiểm tra/tải chương mới và mở ID kế tiếp trong thứ tự truyện. Không lấy Đọc tiếp để chọn lại chương đầu. Offline hoặc lỗi vẫn ở chương hiện tại.
- Lọc liên kết điều hướng Đọc tiếp khỏi parser và mục lục đã lưu; không xóa chương/cache thật. Nhóm Tập khác chỉ biến mất nếu sau lọc không còn chương thật.
- Thay biểu tượng đồng hồ bằng nút Sắp xếp chương: cũ nhất trước hoặc mới nhất trước, lưu theo truyện. Chỉ đổi thứ tự hiển thị; không đổi thứ tự đọc, vị trí đọc hay hàng tải.

Giữ package vn.nanase.hako, khóa ký, SQLite v3, cache và phạm vi tải. Kiểm tra: toàn bộ JVM/JS, cache ảnh, trạng thái đọc/counter cập nhật theo quy tắc mới, và SequentialReadTest trên Android 11. Kiểm tra hàng chờ offline, server tip không đổi/đổi, minh họa bổ sung, sắp xếp qua nút thật và chuyển từ chương cuối cũ tới chương chữ mới. Không gửi thử vào tài khoản Hako thật của người dùng; kết quả chạy cụ thể nằm trong workflow phát hành.
