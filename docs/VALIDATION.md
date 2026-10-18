# Hồ sơ kiểm chứng1.0

## Phân biệt phiên bản

Mã chức năng gốc: `7237d2abbfb2a3cd499ef119e804541c415528aa` (Pocket0.12.1/code71), server `91b7fab5fa4647a34e0b2d1ca6f2d464eeb61365`. Mốc1.0.0/code72 giữ chức năng này, chuẩn hóa tên phát hành và tài liệu. Bằng chứng1.0 được ghi bổ sung trong RELEASE_MANIFEST, không tự gắn kết quả0.12.1 cho APK khác.

## Bằng chứng đã có trước mốc1.0

| Kiểm tra | Kết quả/phạm vi |
| --- | --- |
| Server pytest | 87 đạt |
| CI39134320048 | Thành công: JVM/JS, Android regression,4 cấu hình fixture và APK |
| S4 thật, QA0.12.1 | 93 kiểm tra,26 ảnh, không ca thất bại |
| K60 thật, QA0.12.1 | 69 kiểm tra,19 ảnh, không ca thất bại |
| Cold anchor bằng mẫu | Nhận locator trước file, mở đúng đoạn chữ dù ảnh khác |
| HTTPS sau deploy | health/library/status/resume trả200; thư viện136 truyện khi đo |
|10 warm resume trên PC | Trung vị15,5ms elapsed HTTPS, không phải CPU hoặc tốc độ mở chương điện thoại |
| Restore cô lập trước đó | SQLite integrity OK,134 truyện/104 chương cache/2869 file; không ghi đè dữ liệu sống |

Ảnh chụp sau render và có window metadata. Fixture dùng QA package riêng. Kiểm thử mẫu không truy cập nguồn thật; không dùng nó chứng minh đăng nhập/ảnh/ghi Hako thật.

## Chưa nghiệm thu toàn bộ

[KNOWN_LIMITATIONS](KNOWN_LIMITATIONS.md) liệt kê phần còn mở. Hồ sơ đầy đủ86 yêu cầu và19 hành trình được giữ trong bộ bàn giao; không đánh dấu toàn bộ đạt. Xem `RELEASE_MANIFEST.md` để biết APK/CI cuối cùng.

## Tái kiểm

Chạy BUILDING và fixture trên cả hai máy. Sau đó kiểm mạng thật riêng: hai chiều Đọc tiếp có/không có file; ACK vị trí; upload local và tải lại; offline/reconnect; đọc hết Hako; ảnh K60/no-image S4; tải/pause/full/xóa; layout phím/gesture; backup/restore và hash APK tải công khai. Mỗi ca ghi phiên bản, điều kiện, quan sát và lỗi còn mở.

Hồ sơ từng mục: [86 yêu cầu và19 hành trình](ACCEPTANCE_SCOPE.json). Trạng thái trong hồ sơ là phần kiểm chứng còn mở, không phải danh sách đã đạt.
