# Pocket 0.8.3: ứng dụng chung, ưu tiên server

Giữ package `vn.nanase.hako`, khóa ký, SQLite v3, mã chương và cache của Pocket. Cài đè để giữ dữ liệu.

## Nguồn nội dung

1. Chương hợp lệ trong cache mở ngay bằng NativeReader.
2. Khi đã liên kết, mục lục/chương/tìm kiếm được yêu cầu từ Ubuntu trước. Server chuẩn bị HTML đã trích xuất và tài nguyên ảnh có xác thực, app ghi vào cache hiện có.
3. Server lỗi thì chuyển sang Hako trực tiếp, có khoảng nghỉ trước lần thử server kế tiếp.
4. Điện thoại (K60) dùng Android System WebView cho đăng nhập và nguồn trực tiếp; máy e-ink S4 giữ Gecko. Hai engine có phiên đăng nhập riêng. Không tải engine trong luồng đọc offline.

## Ảnh

S4 giữ giới hạn cũ. Điện thoại giải mã ảnh màu theo màn hình, không nén ảnh trực tiếp xuống 480 px. Ảnh đã tải hợp lệ được dùng lại. Chạm ảnh để xem toàn màn hình, chạm đúp/hai ngón phóng to, kéo và xoay. Chữ vẫn đọc được nếu ảnh còn thiếu; ảnh thiếu được hỏi lại server có giới hạn.

## Đồng bộ

Thoát chương vẫn tạo checkpoint theo cơ chế bridge hiện có. Lần liên kết đầu tiên nhận tiến độ chung, không phát lại hàng loạt vị trí cũ trong máy thành sự kiện mới. Ubuntu và Cloudflare tiếp tục làm nơi đồng bộ; WebView chỉ thay engine nguồn trực tiếp trên điện thoại.

## Khám phá

K60 có ô tìm kiếm trực tiếp. Danh sách có lối vào Cập nhật / Truyện mới / Hoàn thành và chuyển trang nguồn, giữ bộ lọc loại truyện. Trang rỗng có chỉ dẫn quay lại hoặc đổi từ khóa. Từ khóa gần nhất được giữ trên máy.

## Kiểm tra cần phân biệt

JVM kiểm tra chính sách/parser; pytest kiểm tra API/cache/ảnh có xác thực. Build Android và kiểm tra trên S4/K60 được ghi ở báo cáo phát hành. Các kết quả JVM không chứng minh đăng nhập Hako/WebView thực tế.

## Tên miền thủ công

Trên `/hako`: Tài khoản Hako → Thiết lập nâng cao → Tên miền Hako → Lưu tên miền. Có thể nhập tên miền ngoài các gợi ý; không cần sửa mã hay build lại. Server ghi nhớ các tên miền đã lưu để dữ liệu cũ vẫn đồng bộ được sau khi đổi địa chỉ.

Trong app: Cài đặt → Tên miền HAKO. Mục lục nhận từ server/Cloudflare được quy về tên miền đang chọn trên từng máy, giữ nguyên ID truyện/chương và vị trí đọc. S4 và K60 có thể chọn hai mirror khác nhau.

Khi thoát chương, vị trí vẫn được gửi kể cả khi chưa lật trang; cache không cần ghi lại chỉ để tạo checkpoint. Cloudflare không dùng được thì việc nhận vị trí quay về Ubuntu.

## Kiem tra phat hanh 2026-10-18

Pocket 0.8.2 da cai de tren S4 Android 11 va K60 Android 14, giu du lieu. K60 dang nhap bang System WebView, tim truyen, mo anh mau va xoay anh thanh cong. Hai chieu S4 -> K60 va K60 -> S4 da khop chuong 195 cua truyen-28753, quote bat dau tai paragraph 144. Backend 51 pytest passed; APK 0.8.2 CI 13 Android suites / 323 checks.

0.8.3 sua UI bi cho khoa LibraryState khi dong bo muc luc: peek tra snapshot da cong bo ngay neu refresh dang ban. Read mark da co khong kich hoat tinh lai summary. Co regression test UI peek trong luc refresh giu khoa; ket qua CI va kiem tra thiet bi 0.8.3 se duoc cap nhat khi hoan tat.
