# Hako Pocket — 0.7.15 Gecko

Ứng dụng đọc Hako offline cho Xteink S4: Android 11, ARM 32-bit, màn hình e-ink. `main` là bộ source chính thức, thống nhất từ phiên bản 0.7.15 người dùng đã phản hồi chạy khá ổn.

## Sử dụng

1. Cài đè APK cùng package và chữ ký để giữ dữ liệu. Không gỡ app hoặc xóa dữ liệu.
2. Mở **Tài khoản**, đăng nhập Hako và hoàn tất xác minh trong Gecko; quay về app.
3. Mở **Tủ sách** hoặc bấm **Cập nhật**. App nhập thông tin kệ sách trước, rồi tải các truyện thuộc phạm vi đã chọn.
4. Dùng **Đọc tiếp**, **Vừa đọc**, **Yêu thích** để đọc nội dung đã lưu. Trong mục lục có lối đọc tiếp.
5. Trong **Vừa đọc**, chọn **Tải full** hoặc tự đánh dấu **Hoàn thành** khi muốn lưu cả bộ. **Đã tải xong** chỉ hiển thị bộ được đánh dấu hoàn thành và đã có đủ chữ offline theo mục lục đang biết.
6. Giữ Vol+/Page Up khoảng 700 ms để khóa hoặc mở cảm ứng. Phím ngắn lật trang; khóa không chặn thanh thông báo Android.

## Trang chính

| Hàng | Ô 1 | Ô 2 | Ô 3 |
| --- | --- | --- | --- |
| 1 | Đọc tiếp | Vừa đọc | Yêu thích |
| 2 | Tủ sách | Mới cập nhật | Đã tải xong |
| 3 | Cập nhật | Tạm dừng/Tiếp tục | Tìm kiếm |
| 4 | Tài khoản | Cài đặt | Thoát |

Trạng thái tải và tải danh sách có vùng cố định, để thẻ truyện không nhảy khi thông báo thay đổi. Truyện ghim dễ tìm; ghim không đồng nghĩa đọc nhiều hoặc được tải full.

## Những quy tắc quan trọng

- Đồng bộ tủ sách không tự tải tất cả truyện đang theo dõi trên web.
- Truyện thực sự đọc trong APK nhưng chưa lưu/theo dõi/yêu thích: tải từ chương hiện tại đến 15 chương phía sau; giữ ba chương phía trước.
- Truyện theo dõi hoặc yêu thích **và đã đọc trong APK**: tải từ chương hiện tại đến cuối; giữ ba chương phía trước.
- Tải full/Hoàn thành: tải đủ bộ và giữ toàn bộ. Hoàn thành do người dùng đánh dấu.
- Chữ offline hợp lệ được dùng chung giữa mọi danh sách. Thiếu ảnh không khiến app tải lại chữ.
- Quét tủ sách thủ công và tự động đều có thể dừng sớm khi neo đã được đối chiếu; thiếu bằng chứng thì quét đầy đủ.
- Đọc hết offline không tự xác nhận toàn bộ truyện trên Hako nếu mục lục server còn chương chưa đọc.

Chi tiết và ngoại lệ: [quy tắc vận hành](docs/OPERATING_RULES.md).

## Build và kiểm tra

Cần JDK 17, Node.js, Gradle 9.3.1, Android SDK 36 và build-tools 35.0.0.

```sh
bash test.sh
bash build.sh
```

APK nằm trong `build/deliver/`. `version.properties` là nguồn phiên bản duy nhất cho build; `gecko-version.txt` cố định engine. Cấu hình giữ package `vn.nanase.hako`, minSdk 26, targetSdk 34 và SQLite v3.

[Hướng dẫn build](docs/BUILDING.md) · [GitHub Actions](https://github.com/nanasekoto/HakoReaderII/actions)

## Tài liệu source

| Tài liệu | Nội dung |
| --- | --- |
| [OPERATING_RULES](docs/OPERATING_RULES.md) | Quy tắc người dùng, phạm vi tải, ưu tiên, cập nhật, đọc hết |
| [ARCHITECTURE_INVARIANTS](docs/ARCHITECTURE_INVARIANTS.md) | Thành phần, luồng xử lý, cache, dữ liệu, UI, điều phối |
| [BUILDING](docs/BUILDING.md) | Build local/CI, ký APK, kiểm tra emulator |
| [VALIDATION](docs/VALIDATION.md) | Bằng chứng đã có và giới hạn kiểm chứng |
| [MAINTENANCE](docs/MAINTENANCE.md) | Cách sửa tiếp, kiểm tra lỗi, danh mục file đã dọn |
| [AGENTS](AGENTS.md) | Hướng dẫn dành cho công cụ bảo trì source |

Gecko chạy riêng khi cần truy cập web; đọc offline dùng bộ đọc native. Phiên đăng nhập được giữ trong profile Gecko. Khi Hako yêu cầu xác minh mới, người dùng xử lý trong Tài khoản. Không cam kết mọi thử thách Cloudflare luôn thành công hoặc đưa ra số liệu pin/RAM chưa đo.

Các giấy phép font và jsoup đi kèm được giữ trong source và assets. Khóa ký hiện có được giữ để tương thích cài đè bản cá nhân; đây không phải mô hình phân phối công khai bằng một khóa bí mật.
