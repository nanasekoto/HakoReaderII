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

## Kiểm tra phát hành 2026-10-18

Pocket 0.8.3 đã cài đè trên S4 Android 11 và K60 Android 14, giữ dữ liệu. K60 đăng nhập bằng System WebView, tìm truyện, mở ảnh màu và xoay ảnh thành công. Hai chiều S4 → K60 và K60 → S4 đã khớp chương 195 của truyen-28753, đoạn bắt đầu tại paragraph 144. Sau khi cập nhật 0.8.3, cả hai máy mở lại đoạn này và thoát chương bình thường; không thấy ANR/crash mới trong log của lượt kiểm tra.

Backend: 51 pytest đạt. CI 39063528610: JVM/parser/JS, 13 bộ kiểm thử Android với 324 checks, build ARM32 và kiểm tra APK đều đạt. Bài kiểm tra mới giữ khóa refresh và xác nhận UI lấy snapshot ngay. 0.8.3 sửa peek chờ khóa khi đồng bộ mục lục; read mark đã có không kích hoạt tính lại summary.

APK công khai đã đối chiếu SHA256: `3930c8dd9d1a05717ece481c109e0b30cab0ab0855af8520f399eabb559b8c2d`. Thiết lập giữ sáng màn hình tạm thời trên cả hai máy đã trả về giá trị ban đầu.

Cloudflare đã chạm hạn mức đọc D1 miễn phí trong lượt kiểm tra; chưa xác nhận phục hồi trực tiếp khi Ubuntu tắt. Đồng bộ qua Ubuntu đã kiểm tra hai chiều. Zoom hai ngón đã triển khai nhưng chưa xác nhận bằng thao tác trên máy thật.
