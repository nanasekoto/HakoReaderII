# Ràng buộc bảo trì1.0

- Giữ `vn.nanase.hako`, chứng chỉ ký, SQLite3, ID và đường cache. Không gỡ/xóa dữ liệu để khắc phục lỗi.
- Offline native không khởi browser. Gecko chỉ nguồn trực tiếp S4; System WebView nguồn trực tiếp K60.
- Kiểm cache bằng nội dung; ready/exists/size không đủ. Thiếu ảnh không tải lại chữ tốt. Catalog mất chương không xóa lịch sử/file.
- Không scan kho trong adapter/lật trang. Snapshot worker, transaction ngắn, vùng status được chừa trước.
- Hai layout riêng; đo fontScale/vùng nhìn. E-ink không request/decode ảnh.
- Xét scope trước priority. Tải15/trigger14, full theo ý định. Busy/pause/auth không làm mất hàng chờ.
- Checkpoint trước gửi; ACK đúng event, file đi sau vị trí. Không phản hồi cũ kéo vị trí mới hoặc active reader.
- Upload hash/idempotence; không ghi đè chữ tốt bằng dữ liệu lỗi. Cleanup không xóa bản duy nhất/pending/truyện dở.
- Đọc hết cần catalog/snapshot, không suy unread=0. Không tự vượt đăng nhập/xác minh.
- QA gói riêng, không instrumentation phá dữ liệu production. Emulator/máy thật/nguồn thật ghi riêng.

Xem OPERATING_RULES, BUILDING và VALIDATION hiện hành. Tài liệu lịch sử không thay các ràng buộc này.
