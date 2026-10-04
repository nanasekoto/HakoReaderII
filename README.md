# Hako Pocket 0.3.0 — bản thử nghiệm cho Android 11 / Xteink S4

## Trạng thái bàn giao

APK đã biên dịch và xác minh chữ ký. Kiểm tra JVM đã chạy. Bộ kiểm tra Android đã biên dịch nhưng **chưa chạy trên thiết bị hoặc emulator**. Chưa có số đo RAM, pin, bóng mờ hay độ mượt trên S4. Không coi đây là bản đã được kiểm thử hoàn chỉnh.

## Cài đặt và bắt đầu

1. Cài `Hako-Pocket-0.3.0.apk`. Cùng package/chữ ký phát triển với Hako Pocket 0.1 để có thể cập nhật tại chỗ. Không gỡ bản cũ nếu muốn giữ dữ liệu.
2. Trang chính → Tài khoản → tự đăng nhập Hako.
3. Trang chính → Tủ sách → Cập nhật. Thao tác này lấy danh sách, chưa tải hàng loạt chương.
4. Chọn truyện. Bộ chưa có tiến độ sẽ mở mục lục để chọn chương.
5. Đọc truyện ngoài tủ sách: Mở liên kết hoặc duyệt Hako, mở trang chương rồi bấm Đọc offline.
6. Thử không cần tài khoản: Cài đặt → Đọc mẫu offline → chọn chương.

## Đọc

- Bộ đọc native dùng StaticLayout và vẽ các dòng trọn vẹn theo trang; nội dung lưu không cần mạng hoặc JavaScript để đọc lại.
- Chạm nửa trên lùi, nửa dưới tiến; hết/đầu chương có xác nhận trước khi đổi chương.
- Phím âm lượng, Page Up/Down và trái/phải dùng lật trang. Cần thử mã phím S4 thực tế.
- Vuốt dọc rồi chạm để ẩn/hiện công cụ. Khi chạm lật trang tắt, chạm vùng chữ bật/tắt công cụ.
- Menu: khóa chạm, đầu/cuối chương, chương trước/sau, bookmark đoạn, tải lại, dừng tải, đánh dấu bắt kịp.
- Ký hiệu ✎ mở chú thích; bắt chạm chú thích trước thao tác lật trang.
- Icon nguồn thoát thẳng ra launcher và dọn nội dung truyện chưa theo dõi. Back từ bộ đọc về trang chính.
- Font: Tinos, DejaVu Serif, sans hệ thống, nhập TTF/OTF; chỉnh 0.5 sp, đậm, lề dp và giãn dòng. Không gọi font serif hệ thống là Times New Roman.
- Dữ liệu vị trí lưu theo khối đoạn và tỷ lệ ký tự trong khối; khi đổi font tạo ranh giới trang tại dòng chứa vị trí đó. Định dạng HTML phức tạp, bảng/ruby có thể bị giản lược khi chuyển sang native.

## Danh sách

- Vừa đọc lưu metadata trên APK; đường dẫn lịch sử Hako có thể mở riêng. Khi bấm Trang chính từ trình duyệt tích hợp, app nhập lịch sử localStorage của trình duyệt đó nếu mới hơn dữ liệu APK. Đây không phải đồng bộ lịch sử giữa các thiết bị.
- Thường đọc đếm lần mở một bộ trong phiên đọc, có ghim qua nhấn giữ truyện; không tăng số lần theo từng trang/chương tải trước.
- Tủ sách nhập đủ trang theo thứ tự Hako, lấy bộ đếm mới từ data-unread/update-status, có thời gian đọc gần nhất trên APK. Menu có bộ lọc còn chương mới.
- Mục lục phân biệt Đã mở với dấu ✓ nội dung offline. Tải sẵn không tự ghi chương đã mở.
- Mới cập nhật / Truyện mới mở đúng danh sách web khi bấm, không tự tải từ trang chủ.
- Lưu vào tủ sách chạy nút theo dõi thực trên Hako, chỉ lưu trạng thái theo dõi cục bộ sau khi trang xác nhận.
- Đã bắt kịp chạy nút .mark-read của đúng bộ trên kệ sách, sau xác nhận của người dùng. Không GET toàn bộ chương để giả đồng bộ; không hứa làm xám chương trên điện thoại.

## Nội dung và tải trước

- Một WebView ngắn hạn tải trang, chờ nội dung hiển thị ổn định rồi trích #chapter-content, lọc mã thực thi/banner, ghi file nguyên tử. Không lưu phần bảo vệ còn chưa hiện chữ.
- Không gắn JavaScript bridge vào trang từ xa. Hủy renderer sau khi lấy nội dung.
- Lưu/khôi phục reading_series có điều kiện để giảm việc tải trước làm thay đổi lịch sử web. Cơ chế này cần kiểm thử runtime; không phải một kho trình duyệt cô lập hoàn toàn.
- Giới hạn 15 chương phía trước; giữ 3 chương phía sau đã có trên máy, không tự tải lại 3 chương cũ.
- Delay chương 1–2: 4 giây; 3–5: 12 giây; 6–10: 30 giây; 11–15: 60 giây. Không bảo đảm tránh mọi giới hạn máy chủ.
- HTTP 403/429 hoặc trang xác minh: dừng, nghỉ 30 phút. Không tự giải CAPTCHA. Chỉ đặt một lịch chờ, không giữ WebView trong thời gian nghỉ.
- Khi dùng pin, tự dừng hàng đợi ở khoảng chờ nếu màn hình tắt/rời bộ đọc. Một chương đang tải có thể hoàn tất trước khi dừng.
- Tải toàn tủ sách: lịch Android yêu cầu sạc + mạng hoặc nút Đồng bộ ngay. Lịch sạc theo chu kỳ do Android quyết định, không hứa chạy ngay lúc cắm sạc.
- Chưa theo dõi: giữ nội dung tạm khi đọc, xóa nội dung lúc rời bộ đọc/thoát; metadata và vị trí vẫn giữ. Chuyển nền/tắt màn hình không tự xóa nội dung.
- Ảnh minh họa chỉ tải máy chủ Hako cho phép; ảnh lỗi có thông báo thiếu, có nút tải lại. Không tải cover/EPUB.

## Giới hạn cần thử trên thiết bị

Đăng nhập WebView, nút theo dõi/đánh dấu Hako, trang có bảo vệ, chú thích thật, ảnh, khôi phục vị trí, thao tác phím và nền/sạc đều cần kiểm thử Android. Hiện mới có bằng chứng cơ chế website và kiểm tra logic/biên dịch; không có kết quả chạy APK trực tiếp.

Nâng cấp từ DB v1: giữ tiến độ/metadata nhưng phải Cập nhật tủ sách để xác nhận lại theo dõi; trạng thái ready cũ bị đặt lại. Nâng cấp bộ đọc v3 giữ chỉ số đoạn và đặt lại độ lệch trong đoạn do cách tính cũ khác native. Không có đồng bộ đám mây vị trí chính xác.

## Build

JDK 17, Android SDK platform 35 và build-tools 35.0.0; jsoup đã kèm.

```sh
export ANDROID_SDK_ROOT=/duong/dan/android-sdk
bash test.sh
bash build.sh
bash test-android.sh
```

APK chính: build/Hako-Pocket-0.3.0.apk. Khóa kèm theo chỉ dành cho bản thử cá nhân, không dùng phát hành công khai.

## Chạy kiểm tra Android (thiết bị thử nghiệm)

Instrumentation tạo bộ dữ liệu demo và thay đổi tùy chọn tải khi sạc. Chỉ chạy trên máy/AVD thử nghiệm.

```sh
adb install -r build/Hako-Pocket-0.3.0.apk
adb install -r build/test-android/tests.apk
adb shell am instrument -w vn.nanase.hako.tests/.SmokeTest
```

Không cần chuyển tài khoản hoặc mật khẩu cho người phát triển.
