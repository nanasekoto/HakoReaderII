# Bảo trì và dọn source

main là nguồn phát hành, dựa trên 0.7.15 đã được người dùng chấp nhận. Thay đổi tính năng tiếp theo làm trên nhánh riêng; không lấy lại code WebView hoặc policy cũ chỉ vì tên file giống nhau.

## Trình tự sửa lỗi

1. Đọc OPERATING_RULES và ARCHITECTURE_INVARIANTS.
2. Thu thập bước thao tác, phiên bản, giai đoạn trạng thái và báo cáo đồng bộ; phân biệt lỗi quét kệ, mục lục, chọn phạm vi, cache, tải và UI.
3. Đối chiếu ID/khóa chương và phạm vi cần tải trên worker; không yêu cầu người dùng đọc database để chứng minh lỗi.
4. Sửa đúng điểm gọi/policy liên quan. Không xóa cache/database để giấu lỗi.
5. Chạy test hiện hành; thêm test nếu có điều kiện lỗi cụ thể cần giữ. Build một lần sau khi test đạt; chỉ mở rộng thử khi thay đổi hoặc lỗi mới đòi hỏi.
6. Ghi rõ thay đổi, kiểm chứng và giới hạn; không báo S4 đã kiểm chứng từ kết quả JVM.

## Bản hợp nhất source

| Đã bỏ | Lý do / thay thế |
| --- | --- |
| workflow main.yml, engine-071.yml | Một build.yml thống nhất phiên bản và kiểm tra |
| workflow behavior-fix.yml | emulator.yml thủ công, không gắn nhánh/phiên bản cũ |
| gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties | Wrapper thiếu JAR, không chạy; dùng Gradle 9.3.1 đã cài |
| scripts/select-gecko.py | Chọn tự động engine không còn cần; giữ gecko-version.txt cố định |
| WebSession.java | Helper WebView không còn có caller; Gecko là engine thực |
| assets/page.js | Script cũ không còn được tham chiếu |
| READING_SHELF_072.md, SHELF_PRIORITY_074.md | Quy tắc cũ/trùng được thay bằng OPERATING_RULES hiện hành |

README, tài liệu kiến trúc/kiểm chứng, build.sh và test.sh được viết lại. Version root áp dụng cho cả build local/CI. Test JVM trước đây chỉ nằm trong một workflow được gom vào test.sh; vẫn giữ toàn bộ test Android, font, giấy phép, assets Gecko, khóa ký và dependency cần thiết.

Lịch sử Git vẫn giữ các commit cũ. Không dùng nội dung tài liệu cũ trong lịch sử làm quy tắc vận hành hiện hành.

## Nguồn phiên bản

Chỉ sửa version.properties khi phát hành phiên bản app mới. gecko-version.txt chỉ đổi khi chủ động nâng engine và có kiểm chứng ABI/API. Không dùng tên artifact hay chuỗi mô tả trong tài liệu để quyết định phiên bản APK.

## Tính toàn vẹn khi cài đè

Giữ package, khóa ký, schema SQLite v3 và đường cache. Nếu sau này cần migration, thiết kế và kiểm tra bảo toàn dữ liệu trước phát hành. Hạ versionCode hoặc cài APK khác chữ ký có thể bị Android từ chối; không hướng dẫn gỡ app như bước xử lý mặc định.
