# Quy tắc vận hành1.0

Tài liệu này thay quy tắc0.7–0.12 khi mâu thuẫn. Lịch sử không là cấu hình hiện tại.

1. Pocket là app chính; Ubuntu chuẩn bị nội dung và đồng bộ duy nhất. Không dùng Cloudflare trong luồng hiện hành.
2. Giữ package/chữ ký/SQLite v3/ID chương/đường cache. Không xóa dữ liệu để sửa lỗi.
3. Layout S4/K60 độc lập, chung dữ liệu. S4 không tải/giải mã ảnh trong chế độ e-ink; K60 ảnh màu đúng tỷ lệ.
4. Snapshot local trước, cập nhật nền sau. UI không scan kho file khi dựng hàng/lật trang. Thiếu ảnh vẫn dùng chữ hợp lệ.
5. Thao tác trực tiếp ưu tiên trước batch; delay2giây chỉ batch. Server có concurrency hữu hạn và gộp việc trùng.
6. Tự tải tối đa15 chương chữ gồm chương bắt đầu, trigger14. Favorite/follow không tự tải full. Nhập kệ không tải mọi truyện.
7. Full là yêu cầu riêng. Pause giữ ý định; Resume đúng scope; Stop không xóa tiến độ/read-sync. Mở đọc không tự bật mọi tải nền.
8. Rời chương checkpoint local trước; vị trí gửi trước file. Chỉ báo đã gửi sau ACK đúng event; ACK cũ không xóa ý định mới, không kéo vị trí active reader.
9. Neo theo nội dung, không số trang. Máy nhận giữ locator trước khi có file. Chương đã đọc cộng dồn; đọc tiếp không lấy trang xa nhất.
10. Thoát chương không xóa ngay. Cleanup theo tuổi/ngân sách/bản được ACK; bảo vệ bản duy nhất, truyện dở/full/bookmark/outbox.
11. Phân loại chữ/minh họa/chưa rõ theo tập+tên+nội dung. Không bỏ chương ngắn/thông báo vì ít từ. Sửa ảnh riêng.
12. Đọc hết local khác ghi Hako. Ghi nguồn cần mục lục tươi, ID đã đọc, generation/snapshot và ACK thật; thiếu bằng chứng giữ chờ.
13. Đổi nguồn/host giữ ID/cache. Cookie riêng từng origin/engine. Offline native không khởi browser.
14. Dữ liệu/phiên/khóa không vào Git. Báo cáo gửi chủ động, log tắt được. Fixture dùng gói QA riêng.
15. Build đạt không bằng nghiệm thu tổng thể; mọi kết luận gắn phiên bản/phương pháp/bằng chứng. Chưa chạy/bị chặn không được ghi đạt.
