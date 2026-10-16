# Hoàn thiện .38 theo báo cáo Gemini — 16/10/2026

Nền476dfa05662cb47d7f026468575fe868297a35ee, PR8. Báo cáo là đầu vào thẩm định, không áp dụng nguyên code mẫu.

- F38-01: test null-safe active root và interactive windows, kiểm bounds/visible, chạm tâm dòng với touchscreen source qua Instrumentation; readiness theo điều kiện có timeout. Không bỏ assertion bookmark→persistence.
- F38-02: status khởi tạo và receiver cùng bảo vệ thông báo thao tác15giây; callback lỗi thời không đổi màn mới, bỏ trạng thái đang chạy khi rời màn. Metadata mutation dùng snapshot hiện có, không thêm polling UI.
- F38-03: push navigation bằng libraryListMode/storyInformation/bookId/chapterId/tocSection thật; phục hồi snapshot trên worker, giữ tập/scroll. Header pin dùng libraryAction, renderVolume trực tiếp từ snapshot vừa chuẩn bị.
- F38-04: thiếu JSON thì StoryInfo.local hiện thông tin biết chắc + TOC và menu. Không suy server completed từ trạng thái local, không gọi constructor không có. Offline không khởi Gecko để hỏi lại ngay; làm mới khi có mạng theo yêu cầu.
- F38-05: các menu và notification gọi chung stopDownloads. Clear hai SharedPreferences namespace download_resume và paused_requests, không remove nhầm key trong settings. Giữ cache/read queue/global pause. Stop generation loại lệnh service đã được nhận trước STOP; chặn worker ghi lại resume sau STOP. Tác vụ web đã đi ra có thể cần hoàn tất request hiện tại trước khi nghỉ.
- F38-06: jump ẩn toolbar; follow lên HAKO rồi schedule read-sync và cập nhật mô tả nút cùng feedback.
- F38-07: font tmp dọn finally. Sửa5nhóm assertion legacy đã nêu; chưa bật workflow manual SmokeTest hoặc gọi nó PASS nếu chưa thực thi. Gate hiện hành vẫn12suites.

Code mẫu được hiệu chỉnh: các field storyInfoScreen/currentListTitle/chapterScreen/currentStoryInfo và constructor StoryInfo trong mẫu không khớp source. completed/info không là field của Book. libraryAction không dùng return string của Callable làm kết quả UI. Notification STOP cũ cũng chỉ cancel, không có đủ cleanup như báo cáo mô tả. Không nhận câu “không có P0/rủi ro bằng0” là chứng minh an toàn tuyệt đối.

Critical path: click chọn state/feedback; DB/catalog/info/font ở worker. Background: snapshot persist, download/read-sync và log theo chính sách cũ. Không thêm timer CPU hay quét thư viện trên page turn. Giữ package/chữ ký/schema3/cache/watermark và batch15/trigger14; manual-only email.

Kiểm tra: test.sh trước; emulator chạy UxLinkTest,TocWorkflowTest,FeatureFlowTest trước, chỉ sau PASS mới chạy9suites còn lại, rồi ARM32 release. Một emulator, không chạy lặp3suites đã qua. Bằng chứng CI cuối và hash APK ghi báo cáo phát hành; tài liệu này không tự nhận tests đã PASS.

Rollback: revert batch với versionCode phù hợp cài đè; không gỡ app/xóa dữ liệu. Còn cần xác nhận S4 thật cho phím/màn/pin, không suy từ emulator. Không merge PR8 tự động.
