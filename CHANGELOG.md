# 1.0.3 · S4 navigation

Nút làm mới ở góc phải Khám phá/Mới cập nhật; lấy chi tiết trước khi rời danh sách. Chi tiết S4 có chữ lớn hơn và phân bổ khoảng trống, giữ đủ tên/tag; giới thiệu dùng font/cỡ chữ/giãn dòng đang chọn của trình đọc. Bỏ nút làm mới thông tin. Danh sách S4 có đuôi trắng để trang cuối giữ truyện ở đầu màn hình, thêm bộ đếm trang/dải truyện và nhãn Cuối. Build-only và cài đè S4 theo yêu cầu; không test/giả lập. [Chi tiết](docs/S4_NAVIGATION_103.md).

# 1.0.2 · S4 layout

Điều chỉnh theo 9 ảnh của chủ sở hữu: badge chưa đọc bên phải, dòng Vừa đọc gọn, khám phá gọn và bộ đếm nhỏ, các nút chi tiết chung hàng, giới thiệu phân màn riêng. Sửa nhận thứ tự kệ từ server. Build-only; không chạy test, chủ sở hữu kiểm tra. [Chi tiết](docs/S4_LAYOUT_102.md).

# Thay đổi

## 1.0.1 — khôi phục khóa cảm ứng

- Giữ Volume Up mở khóa cả khi khóa còn lưu ở Home, trước xử lý phím của danh sách.
- Đường thoát khóa không phụ thuộc tùy chọn volume lật trang; xử lý đủ sự kiện thả để giữ lần tiếp theo vẫn hoạt động.
- Có ca QA khởi động lại khi khóa và hồi quy chuỗi giữ/thả.

## 1.0.0 — mốc chốt đầu tiên

- Pocket/Reader II là app chính, ưu tiên Ubuntu; không phụ thuộc Cloudflare.
- Layout riêng S4/K60; S4 chỉ chữ, K60 ảnh màu. Sửa chiều cao thanh công cụ đọc và nhãn chưa đọc S4.
- Native offline, snapshot thư viện, pack nội dung, tách sửa ảnh/chữ, tải15/trigger14.
- Vị trí ưu tiên, outbox thử lại, neo chữ chung, nhận neo trước file và chặn phản hồi cũ.
- Upload nội dung local theo manifest/chunks/hash với ACK riêng; dọn cache bảo vệ dữ liệu đọc.
- Viết lại tài liệu sử dụng, build, vận hành và giới hạn; giữ lịch sử.

Kế thừa mã chức năng ứng viên0.12.1; versionCode72/versionName1.0.0. Xem VALIDATION để phân biệt phần đã chạy và phần chưa nghiệm thu đủ.
