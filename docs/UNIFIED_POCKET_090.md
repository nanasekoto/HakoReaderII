# Pocket 0.9.0 — cache trước, server chuẩn bị, thao tác theo thiết bị

- Giữ package/chữ ký/schema/cache và dữ liệu đọc hiện có.
- Tủ sách mở snapshot trên máy, nhận kệ đã lưu từ server, không cần phiên Hako của máy trong chế độ server. Server tự cập nhật kệ; không có dữ liệu thành công thì không xóa kệ trên máy.
- Khám phá/tìm kiếm dùng JSON chuẩn hóa và bản lưu nguyên tử trên máy. Hiện bản lưu trước; bản mới không tự chèn gây nhảy danh sách. Server giữ cache và cập nhật nền theo job.
- Trang thông tin cuộn toàn bộ nội dung: bìa, tên đầy đủ, loại truyện theo URL nguồn, thông tin có nhãn, thống kê, toàn bộ giới thiệu/tag và lối vào đọc/mục lục. Không cắt giới thiệu 6000 ký tự; không bịa số liệu thiếu.
- K60: đọc phân trang hoặc cuộn dọc native, vuốt ngang lật trang, tùy chọn phím âm lượng và vùng chạm. Giữ khóa cảm ứng.
- S4: các hàng cài đặt nằm trọn từng trang, chuyển bằng phím hoặc nút Trước/Sau. Bộ đọc e-ink giữ phân trang.
- Cài đặt gồm hiển thị/thao tác, tải/offline và kết nối/nguồn. Các mục chẩn đoán và chuyển dữ liệu cũ được bỏ khỏi menu. Báo cáo có bật/tắt thu thập log và xóa log; không tắt việc lưu tiến độ khi ngừng log.
- Nguồn nội dung: tự động ưu tiên server, chỉ server hoặc Hako trực tiếp. Nút ở Home mở trạng thái, kiểm tra lại, địa chỉ, tên miền trực tiếp và liên kết. Đổi nguồn không tắt đồng bộ tiến độ.
- S4 tải gói ZIP tối đa 15 chương đã nằm trong phạm vi tải được chọn; server gom manifest, metadata/mục lục, nội dung và ảnh đã sẵn sàng. Ảnh nhúng tối đa 8 MiB, client giới hạn ZIP 13 MiB và tổng giải nén 16 MiB, xác thực đường dẫn/ID/chữ. Dữ liệu hợp lệ được dùng lại. Ảnh vượt hạn mức hoặc chưa sẵn sàng tiếp tục cơ chế sửa ảnh có giới hạn; không chặn chữ.
- API pack nhận phiên bản đã biết để trả delta; app hiện chọn chương thiếu qua cache hợp lệ trước khi yêu cầu gói. Chưa có nội dung thì server chuẩn bị job; việc chờ vẫn có các lần hỏi trạng thái, không phải luôn chỉ một HTTP request.

Kiểm tra và bằng chứng phát hành sẽ được ghi sau lượt CI và kiểm tra thiết bị cuối. Không coi kiểm thử fixture là bằng chứng tài khoản Hako thật đã đăng nhập.

Đồng bộ qua Ubuntu dùng một phản hồi JSON nén gzip cho các truyện có tiến độ hoặc đã mở trên máy; fingerprint bỏ qua tài liệu không đổi. Mỗi phản hồi giới hạn 8 truyện / 5 MiB dữ liệu, tiếp tục phần còn lại theo đợt. Không nhập toàn bộ mục lục của truyện chưa đọc chỉ vì có trên kệ. Các cờ hoàn thành/đã đọc không đổi không gây tính lại thư viện. Đồng bộ qua coordinator Cloudflare hiện giữ giao thức cũ.

## Bằng chứng phát hành

- CI 39067661586, source d4b3e19574aa9cb1168dd2796260f94442e1ed86: JVM/parser/JS, 13 bộ Android với 330 checks, release ARM32 và xác minh APK đều đạt. Lượt CI đầu dừng ở assert cũ đợi mục lục inline; đã đổi assert theo nút mở mục lục và cho runner thu toàn bộ lỗi mỗi lượt.
- Backend: 56 pytest đạt; các bài pack, cache restart, delta sync, lazy cover, thống kê có nhãn và credit nhóm dịch đạt sau các chỉnh sửa dữ liệu nguồn.
- APK 0.9.0 code 63 cài đè thành công trên S4 Android 11 và K60 Android 14; không xóa dữ liệu. K60 mở kệ 120 bộ mà không đăng nhập trên máy; Khám phá hiển thị bìa, loại truyện và mở trang thông tin dài tới toàn bộ tag. S4 chuyển cài đặt từ trang 1/2 sang 2/2 bằng phím; hàng cuối kết thúc phía trên vùng Trước/Sau và Đóng/Áp dụng. K60 đã mở chế độ cuộn native và vuốt; đã trả chế độ phân trang sau kiểm tra. S4 thử chọn Chỉ server rồi trả về Tự động. Nút Báo cáo hiện công tắc log.
- Server thật trả gói ZIP của chương 195 truyen-28753, khoảng 12 KB; gói sync gom 3 truyện trong một phản hồi gzip. Không suy ra từ đây rằng mọi đợt 15 chương/ảnh đều có cùng kích thước hoặc chỉ cần đúng một request; gói chưa sẵn sàng vẫn hỏi trạng thái.
- HTTPS APK/page/QR hoạt động; SHA256 `fa7bde393ee807aabd5a2559c42f7b6aff18ab1f8b3d97309575029f5842b91f`. Không thấy crash/ANR mới trong log lượt kiểm tra cuối. Cài đặt giữ sáng tạm thời được trả về giá trị trước kiểm tra.

## Giới hạn quan sát được

Làm mới kệ Hako trên server hiện có lượt timeout chờ bảng; app vẫn dùng kệ đã lưu và thời gian cập nhật thật. Một số lần lấy danh sách bị nguồn đóng kết nối, cache được giữ. Ảnh bìa chưa tải thành công có thể còn trống. Cloudflare outage recovery vẫn chưa xác nhận do hạn mức D1 miễn phí trước đó. Sau khi đổi nguồn trong panel, lựa chọn có hiệu lực ngay nhưng nhãn Home có thể chỉ đổi khi mở lại Home. Không tuyên bố giao diện đã được người dùng chốt là tối ưu hoàn toàn.
