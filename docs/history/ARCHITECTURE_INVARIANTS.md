# Kiến trúc và ràng buộc bảo trì

Tài liệu hiện hành cho main/0.7.15; quy tắc sản phẩm ở [OPERATING_RULES](OPERATING_RULES.md).

## Thành phần

| Nhóm | File chính | Trách nhiệm |
| --- | --- | --- |
| UI và đọc | MainActivity, NativeReader, PagedBookList, PageBreaks, RenderedPage, HoldKey, IconButton, ListPaging | Điều hướng, danh sách, chia trang, vị trí đọc, phím |
| Snapshot UI | UiLibrarySnapshot | Nạp và tính tóm tắt trên worker; adapter chỉ đọc snapshot |
| Dữ liệu | Store | SQLite v3, file chương, trạng thái đọc, tùy chọn, hợp nhất mục lục |
| Đồng bộ/tải | Repository, DownloadService, ChargeJob, ReadSync | Nhập kệ, tải chương, công việc nền, xác nhận đọc |
| Policy thuần | FetchPolicy, DownloadScope, DownloadPriority, DownloadLedger, CachePolicy, CatalogPolicy, ReadPolicy, ShelfPolicy, ShelfPagePolicy, ShelfAnchor | Điều kiện phạm vi, ưu tiên, chống trùng, hợp nhất và xác minh |
| Web | GeckoClient, GeckoHost, GeckoEngineService, GeckoBrowserActivity | IPC, engine riêng, phiên web, màn đăng nhập |
| Trích xuất | HakoParser, assets/extract.js, assets/gecko-bridge | Parse kệ/mục lục, chờ DOM chương, cầu nối Gecko |
| Quan sát | UpdateReport | Báo cáo giai đoạn, checkpoint, lỗi và lý do bỏ qua |

## Luồng web

GeckoBrowserActivity xác minh/đăng nhập trong profile Gecko được lưu trên thiết bị. Repository.page lấy HTML qua GeckoSession có JavaScript. Trang chương chạy script của website; extract.js chờ nội dung rồi trích xuất. HakoParser kiểm tra nội dung trước lưu cache. NativeReader đọc chữ offline và không tạo GeckoRuntime.

GeckoRuntime nằm trong service không export ở tiến trình :gecko. Worker xử lý tuần tự; đăng nhập tương tác chặn yêu cầu nền. Đóng session sau trích xuất, bỏ kết nối sau tác vụ; engine thoát sau khoảng năm giây yên và phần native dọn tiến trình còn cache sau khoảng tám giây không có kết nối. Không khởi tạo CookieManager/WebView khi mở reader, không xuất cookie sang engine khác. Hộp thông tin thiết bị có thể đọc phiên bản WebView hệ thống; đây không phải engine tải Hako.

Không tự thử CAPTCHA liên tục. Lỗi giới hạn truy cập phải hiện lý do và tuân thủ cooldown.

## Dữ liệu và cache

SQLite `hako.db` giữ phiên bản 3. Tên cột SQL là `shelf_rank`, `shelf_info`, không dùng tên field Java camelCase. Book lookup phải xử lý null. Các khóa bổ sung của phiên bản hiện tại nằm trong SharedPreferences; không giả định đã có bảng/cột của thiết kế migration v4 cũ.

Cache chữ chung: `files/chapters/<chapter-id>/content.html`. Ảnh có trạng thái riêng với khả năng đọc chữ. Chương chỉ có ảnh cần mọi file ảnh local được BitmapFactory nhận diện; fingerprint gồm metadata ảnh. Giữ nguồn ảnh để thử lại phần thiếu; không thay ảnh lỗi bằng văn bản rồi tính hoàn tất. Xem IMAGE_CHAPTER_FIX_0716.md. Không dùng duy nhất cờ ready hoặc file.length > 0 để quyết định chữ đã lưu.

CatalogPolicy dùng ID: mục lục web quyết định thứ tự các chương còn hiện; chương cũ thiếu trên web được giữ gần các chương còn tồn tại và đánh dấu ẩn. Bảo toàn file, ID đã đọc và vị trí hiện tại; không ném lỗi chỉ vì một chương cũ biến mất. Mục lục rỗng, ID không hợp lệ/trùng cần báo lỗi và giữ dữ liệu cũ.

Kiểm tra nội dung file và dọn startup chạy ngoài khóa Store dài; thay đổi database theo transaction ngắn. UI không đọc hàng nghìn file hoặc parse HTML trong getView. Fingerprint file là lớp cache tăng tốc sau kiểm tra, không là chứng cứ đầu tiên của tính hợp lệ.

Không tăng schema, đổi package, khóa ký hoặc đường cache trong một bản dọn source. Không xóa chương từ vị trí đọc trở đi khi prune; truyện full giữ toàn bộ, truyện thường chỉ dọn phần cũ hơn ba chương phía trước. Chương ẩn được bảo vệ.

## Điều phối

Repository.busy bảo vệ tác vụ web. Khi bận, lưu yêu cầu chờ; sau ReadSync hoặc lượt tải giải phóng khóa, resumePending phải tiếp tục yêu cầu còn lại. DownloadService xử lý pending giữa các lượt. Không “bấm Cập nhật rồi mất lệnh”. Tạm dừng/hủy là chủ động, trạng thái phải phản ánh điều đó.

DownloadLedger chống trùng ID. DownloadScope xác định truyện được tải trước khi ưu tiên; FetchPolicy xác định cận chương. Không nhầm danh sách web với quyền tải cả thư viện.

ReadSync xác minh mục lục mới và ReadPolicy trước thao tác mark-read. Giữ hàng chờ khi lỗi hoặc chưa đủ điều kiện; không nhận số unread trên web làm bằng chứng tất cả chương offline đã đọc.

## UI tĩnh

Chuẩn bị snapshot trên worker, tái dùng cache danh sách. Chừa vùng trạng thái cố định trước khi hiển thị; chỉ cập nhật chữ, không thêm/bỏ separator, padding hoặc chiều cao giữa lượt. Thẻ đo theo vùng nhìn thực tế; tên tối đa hai dòng, giữ để xem đầy đủ, không cắt nét hoặc góc.

Home lưu tiến độ và về trang chính. Back đi theo lịch sử màn hình đã lưu; không thoát vì quên push trước mục lục. Khóa cảm ứng dùng giữ phím 700 ms, bỏ auto-repeat, hủy timer khi mất focus; thả sau giữ không lật trang. Khóa không điều khiển notification shade của OS.

Native reader không có timer refresh nền hoặc forced screen-on. Trạng thái tải không dùng Toast liên tục. UpdateReport có bộ đệm giới hạn và checkpoint thay vì ghi preferences mỗi dòng.

## Kiểm tra trước thay đổi

Đối chiếu cả [quy tắc](OPERATING_RULES.md), các điểm gọi policy và [test](VALIDATION.md). Một policy đúng nhưng call site dùng overload cũ vẫn có thể sai. Build thành công chỉ chứng minh khả năng biên dịch/ký; không chứng minh toàn bộ hành vi Hako/S4.
