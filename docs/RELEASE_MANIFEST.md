# Định danh Hako1.0

- Pocket **1.0.0/code72**, package `vn.nanase.hako`, ABI armeabi-v7a, Android8+.
- Build commit: `ab71b330d5cbe623d6b8fa05d1d47a8b761022ce`.
- CI: [39136249174](https://github.com/nanasekoto/HakoReaderII/actions/runs/39136249174), kết quả thành công.
- SHA256 APK: `a75085c63d8f9509f49ec12f9318f336af661259806713506ce2caaaa2a47d51`.
- SHA256 chứng chỉ ký: `c9bdde1462883355eed5012120c8153652b32fba1db1ae790d61030260d76eb2`.
- Server chức năng:91b7fab; bộ tài liệu/main server: `6acfb271ff543c7f08d890ade8e7bd12bc63df86`. Giữ health/API2.0.1.
- Pocket tag v1.0.0; server tag suite-v1.0.0, giữ tag server v1.0.0 lịch sử.

## Kết quả đúng phiên bản

1.0 CI: JVM/JS/Android regression và4 cấu hình fixture đều đạt. S4 API30 font1.0/1.3 mỗi cấu hình93 kiểm tra; phone API34 font1.0/1.3 mỗi cấu hình69 kiểm tra. Chữ ký/package/version/ABI được kiểm lại trên APK tải về.

Server87pytest chạy lại đạt. QA máy thật của nền0.12.1: S4 đạt93/26ảnh, K60 đạt69/19ảnh; không mô tả là đã chạy APK1.0 trên máy thật. Khi chuẩn bị phát hành1.0, hai máy không còn kết nốiUSB nên chưa cài đè1.0 và chưa gỡ QA được.

Mã chức năng1.0 không đổi so với0.12.1; khác tên/code và tài liệu/artifact. Merge/doc commits sau build không được coi là đã tạo APK khác. Các ca nguồn thật/chuyển máy/offline/upload còn thiếu xem VALIDATION, KNOWN_LIMITATIONS và ACCEPTANCE_SCOPE; không đánh dấu đạt vì tên1.0.
