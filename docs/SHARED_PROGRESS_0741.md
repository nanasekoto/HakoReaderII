# Cập nhật 0.7.42

Rời chương/flush checkpoint tự gửi vị trí. Sự kiện mới được nơi đồng bộ nhận sau cùng cập nhật vị trí, kể cả đọc lại chương cũ; retry cùng sự kiện không thay sự kiện đã nhận sau đó. Không còn bước chọn bên có tiến độ cao hơn. Khi Cloudflare lỗi, gửi và nhận qua Ubuntu; giữ gói chờ đám mây đến khi giao được hoặc vị trí của máy khác đã thay thế. Pocket đang đọc chỉ lấy snapshot của truyện đó.

# Đồng bộ hai chiều — 0.7.41

## Thiết lập một lần

1. Cài đè Hako Pocket 0.7.41 cùng chứng chỉ để giữ dữ liệu và phiên Hako.
2. Mở `https://nanains.duckdns.org/hako/`, đăng nhập quản lý và tạo mã liên kết.
3. Trong app chọn Cài đặt → Đồng bộ hai chiều với Hako Server, nhập mã.
4. Chủ sở hữu bấm Chấp thuận yêu cầu đúng điện thoại ở trang quản lý.
5. Chờ lần đối chiếu thư viện/tiến độ đầu tiên hoàn tất. Hako Server trên K60 liên kết bằng mã riêng.

## Hành vi

- Bridge không khởi động Gecko, không tự tải mọi truyện được theo dõi và không đổi schema SQLite v3/cache ID.
- Vị trí lưu ngay trên máy, gửi theo sự kiện với debounce. Gói đang chờ được lưu bền khi mất mạng.
- Khi Cloudflare đã cấu hình, hai app dùng chung trạng thái trực tiếp; Ubuntu chỉ cần cho lần liên kết đầu và nguồn/cache server của app mới.
- Mục lục được lưu theo revision; dữ liệu giống nhau không tải/gửi lại toàn bộ mỗi lần lưu vị trí.
- Chương đã đọc là tập ID cộng dồn. Đọc lại chương cũ không xóa dấu đã đọc của các chương sau.
- Khi cả hai máy cùng đọc lúc mất mạng, vị trí gửi lên sau cùng khi có kết nối trở thành vị trí chung; không chọn theo chương xa nhất.
- Đang đọc không tự nhảy sang vị trí từ máy kia. Sau khi rời màn đọc, vị trí mới có thể áp dụng nếu không còn outbox/xung đột cần giải quyết.
- Locator dùng trích đoạn và tỷ lệ trong đoạn để nối cách chia đoạn giữa hai trình đọc; kết quả chính xác trên hai máy thật vẫn cần kiểm tra bằng chương người dùng đang đọc.
- App 0.7.40 không có bridge này. Cả hai điện thoại mất Internet thì chưa nhận được ngay tiến độ của nhau; mở nội dung local và gửi lại khi có kết nối.
- Ghi “Đọc hết” lên Hako vẫn theo cơ chế cũ. Server mặc định không ghi, tránh hai đầu cùng thao tác tài khoản.

Source 213e760 đã qua pipeline đầy đủ, run 39038639814: gates, Android workflow/diagnostics, release ARM32 và xác minh APK. APK tải lại qua HTTPS khớp SHA-256 bản phát hành.
Không dùng kết quả giả lập để tuyên bố đã kiểm chứng trực tiếp S4 hay K60.
