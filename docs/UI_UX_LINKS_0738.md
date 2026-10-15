# UI/UX và liên kết luồng 0.7.38

## Workflow và trách nhiệm

- MainActivity: menu đọc có entry thực trên toolbar; bookmark lấy/chỉnh dữ liệu trên worker. Toolbar ẩn đồng thời; tải lại không hạ ready của nội dung hợp lệ trước request.
- Settings: import font cập nhật draft/nhãn/preview của panel còn hiện; Áp dụng không chọn lại font cũ. Đo CPU áp dụng ngay được ghi trên nhãn. Quét kệ/audit/demo đóng panel trước tác vụ.
- Story info: sort40dp thay ô giãn ngang; Back từ tập lấy lại snapshot qua openStoryInfo. Thao tác mutation có feedback trong dòng trạng thái; refresh snapshot ngoài UI, callback cũ không đổi màn mới. Drop/ghim lấy Book mới trước toggle.
- Main receiver/DownloadService: read_sync_book có owner riêng, không ghi đè download notification hoặc màn truyện khác; bỏ event chỉ metadata/null text.
- GeckoBrowserActivity/HakoParser: chỉ trang tải thành công mới trả committed URL. Automatic rebase nhận site host đã định danh hoặc origin người dùng đã chọn; không nhận i2.hako.vip/assets chỉ vì thuộc cùng suffix. Trang mở thành công không được gọi là đăng nhập thành công.
- Store/ReadSync: demo/local không tạo hàng chờ remote. Tựa chưa theo dõi hoàn thành local trước, khi nhập kệ xác nhận followed sẽ tạo yêu cầu từ completion hiện có. Không dùng local false để xóa yêu cầu legacy hợp lệ. Thiếu nút đã được xác minh vẫn retire theo request generation.
- Pause: lưu scope đang chạy và queued intent trước khi dọn live queue. Resume khôi phục scope đó, không tự yêu cầu nhập cả kệ. Full thủ công được chạy riêng khi pause, không bật tác vụ nền khác; quyền bypass là ThreadLocal chỉ của lượt đó.

## Critical và background

UI click chỉ chọn trạng thái/điều hướng/hiện feedback. DB/bookmark/demo/refresh snapshot vẫn chạy worker. Native reader không khởi tạo Gecko để đọc cache. Không thêm polling, timer đo giữ trang hoặc quét chapter files trên mỗi lần lật trang.

## Invariants

Giữ package/signing/schema3/content paths và read positions. Batch tự động15chữ/trigger14, minh họa không tính chưa đọc, full reset unread, completion novel khác read-all, manual report-only và pause/charging policy giữ theo mục đích đã chốt. Hàng chờ đọc hết vẫn cần catalog tươi và proof server; không giả SENT.

## Verification/rollback

OriginPolicyTest thêm site/CDN/lookalike/custom cases. UxLinkTest kiểm tra queue local/follow recovery, pause scope, full bypass policy và entry menu→bookmark→persistence, font draft. Các Android suites cũ vẫn chạy trước ARM32 release. Kết quả thực và source SHA ghi trong báo cáo phát hành khi CI hoàn tất, không suy test pass từ source.

Rollback cài source trước với versionCode cao hơn nếu cần cài đè; không downgrade/gỡ app/xóa dữ liệu. Các prefs paused_requests chỉ là intention bổ sung, schema không đổi.

## Giới hạn còn lại

Không tuyên bố mọi geometry trên S4 đạt chuẩn từ emulator; toolbar gọn36dp vẫn thấp hơn khuyến nghị48dp touch target. Cần xem thêm khả năng phím cứng/expanded hit area mà không chồng nhau. SmokeTest legacy không nằm trong gate hiện hành; các assert cũ cần thay bằng scenario mới trước khi bật lại, không sửa số cho pass. Cleanup dead code chỉ sau kiểm đầy đủ reflection/manifest/JS/tests. Chưa có bằng chứng để xóa mọi helper trong F16. Các giới hạn này được giữ rõ trong tài liệu Gemini thay vì gọi đã hoàn tất toàn bộ kiểm toán.
