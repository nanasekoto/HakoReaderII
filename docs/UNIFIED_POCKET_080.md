# Pocket 0.8: ứng dụng chung, ưu tiên server

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
