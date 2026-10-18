> Tài liệu lịch sử. Hướng dẫn hiện hành: [Pocket1.0](../README.md). Các mô tả Cloudflare/phiên bản cũ không áp dụng mặc định.

# Hako Pocket 0.7.20 — sửa số chương chưa đọc

Nền: 0.7.19-Gecko, commit 8e5857be9291fbf176c1137b514090f8f9d4a796. Phiên bản mới 0.7.20-Gecko, versionCode 36.

## Lỗi trong 0.7.19

- UiLibrarySnapshot gọi Store.unread trước khi readable nhận diện lại nội dung các chương cũ. Minh họa chưa có chapter_kind bị tính vào số chưa đọc, rồi snapshot không tính lại sau khi đã phân loại.
- Mỗi dòng tập trong MainActivity.contents ghi Store.unread của cả truyện. Một tập chỉ có minh họa vẫn hiện số chương chữ chưa đọc nằm ở tập khác.
- Snapshot đang tải có thể giữ trạng thái trước lúc chương cuối được đánh dấu đã đọc. Yêu cầu làm mới khi đang tải có thể bị bỏ qua nếu không force.
- remaining được tính trong lúc s.total mới đếm đến chương đang đọc, dùng ord chung cả minh họa; kết quả không phản ánh số chương chữ phía sau.

## Sửa

Phân loại cache trên worker trước khi đếm. Tách chữ chưa đọc, minh họa chưa xem và chương ít chữ/chưa rõ cần kiểm tra. Đếm từng tập độc lập; adapter dùng tóm tắt đã chuẩn bị, không quét file khi vẽ dòng.

Store có readingRevision thay đổi khi có lịch sử đọc, chương hiện tại đổi hoặc phân loại nội dung đổi. Snapshot cũ được loại và nạp lại; yêu cầu làm mới khi đang tải được giữ. Khi tới trang cuối, app lưu read_chapters và làm mới dữ liệu thư viện ngay. Chương đã đọc hiện "Đã đọc tới", thay cho "Đang đọc".

Chỉ mở chương chưa đồng nghĩa đọc hết các trang. Đã tới trang cuối thì chương đó được đánh dấu đọc. Không tự đánh dấu các chương chữ phía trước chưa đọc; chương ứng viên vẫn phải được kiểm tra khi gửi Đọc hết lên Hako.

## Kiểm tra

Giữ ImageCacheTest và ReadingStateTest của 0.7.19; thêm UnreadStateTest kiểm tra SQLite, bộ phân loại, snapshot và MainActivity thật trên Android 11. Kiểm tra gồm: minh họa cũ trên snapshot đầu tiên, đếm riêng từng tập, chương mới nhất nhiều trang, tới trang cuối ghi read_chapters, badge chương mới cập nhật, không giả xem ảnh/đọc ứng viên, làm mới ngay dữ liệu UI và giữ vị trí đọc.

Workflow chỉ xuất APK ARM32 sau khi build, xác minh package/version/chữ ký và mọi kiểm tra đạt. Chưa kiểm chứng trên S4 vật lý. Package, chữ ký, SQLite v3, cache và chính sách tải của 0.7.19 giữ nguyên.
