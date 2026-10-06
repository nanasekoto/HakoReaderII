# Hako Pocket 0.7.0 Lite

Android 11 / Xteink S4 · ARM 32-bit · bộ đọc native + GeckoView 156.

## Bắt đầu

1. Cài APK cập nhật tại chỗ để giữ tủ sách và vị trí đọc.
2. Trang chính → Tài khoản, hoặc Cài đặt → Tài khoản HAKO. Đăng nhập và xác minh trực tiếp trong Gecko.
3. Bấm **Về app** → Tủ sách → Cập nhật. Cookie của WebView cũ không được chuyển sang Gecko; cần đăng nhập lần đầu trong engine mới.
4. Bấm tên truyện để đọc tiếp; Yêu thích mở chọn chương. Ngôi sao trong chọn chương thêm/bỏ yêu thích. Icon tải toàn bộ giữ đủ các chương của bộ đó.
5. Giữ Vol+/Page Up khoảng 700ms để khóa/mở cảm ứng trong giao diện native. Bấm ngắn lật trang. Khóa này không chặn thanh thông báo của Android.

## Lite hoạt động thế nào

- Không tích hợp Firefox đầy đủ, nhiều tab, đồng bộ Firefox hoặc tiện ích tải từ bên ngoài.
- APK S4 chỉ chứa thư viện ARMv7; APK x86_64 phục vụ kiểm tra được build riêng.
- Bộ đọc offline không tạo GeckoRuntime và không khởi tạo Android WebView/CookieManager. Văn bản, font và vị trí đọc vẫn thuộc bộ đọc native.
- Đăng nhập, tải tủ sách/mục lục, lấy chương và các lệnh theo dõi/đọc hết dùng chung profile Gecko lưu trên máy. Không xuất cookie sang trình duyệt khác.
- Mỗi lượt chỉ xử lý một yêu cầu web. Sau trích xuất đóng trang; cuối lượt tải bỏ kết nối service. Sau 5 giây không có người dùng hay công việc mới, runtime đóng và tiến trình `:gecko` thoát.
- Chờ DOM chỉ trong trang chương đang tải; không tải lại CAPTCHA tự động, không lưu chương rỗng. Các quy tắc tốc độ tải, đọc full/tiếp theo và hàng chờ đọc hết được giữ từ 0.6.x.
- Mở Tài khoản dừng lượt tải và chặn yêu cầu nền mới trong lúc đăng nhập. Rời màn web sẽ quay về giao diện native.

Gecko là engine trình duyệt thực nên dung lượng không chỉ tăng vài MB. Đóng engine sau công việc giảm tài nguyên khi đọc offline; chưa có phép đo pin/RAM trên S4 để cam kết con số cụ thể. Firefox hoạt động trên máy là cơ sở lựa chọn Gecko, không phải bằng chứng mọi thử thách Cloudflare sẽ thành công trong app.

## Build

JDK17, Gradle9.3.1, Android Gradle Plugin9.1.0, compileSdk36, build-tools35.0.0. `targetSdk34` và `minSdk26` được giữ; compileSdk không có nghĩa phải dùng API36.

```sh
gradle assembleRelease -Pabi=armeabi-v7a
```

Phiên bản Gecko được cố định trong `gecko-version.txt`; không tự nâng engine ở mỗi lần build. Khóa ký kèm repository là khóa phát triển cho bản thử cá nhân.

## Kiểm tra

- `bash test.sh`: parser, phân trang, các quy tắc hồi quy và giữ phím.
- Workflow **Verify Gecko Lite 0.7.0** build ARM32, xác nhận ABI, rồi cài APK x86_64 trên Android11/API30 ở 480×800, mật độ219 và220.
- `SmokeTest`: bố cục sáu thẻ, chọn chương, đọc native, phím khóa, tiến độ và hàng chờ offline.
- `GeckoSmokeTest`: HTTPS thực qua executor, IPC, bộ trích xuất thật với DOM tiếng Việt xuất hiện trễ, cookie còn sau khi đóng/mở tiến trình và engine thoát sau lượt tải. Trang chương trong phép thử là dữ liệu tổng hợp trên trang công khai, không phải một chương Hako thật.

Kết quả từng lần build có trong artifact evidence của Actions. Chỉ run có toàn bộ kiểm tra thành công mới xuất artifact **tested-APK**. Kiểm tra emulator không thay thế xác minh Cloudflare và đo pin trên S4 thật.

Các ràng buộc giao diện và dữ liệu: [ARCHITECTURE_INVARIANTS.md](docs/ARCHITECTURE_INVARIANTS.md).
