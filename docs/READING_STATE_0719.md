# Hako Pocket 0.7.19 — sửa vị trí đọc và tải ngoài ý muốn

Nguồn nền: 0.7.18-Gecko, nhánh fix/illustration-cache-0718, commit bcf5976376da09195358774c28643d7c0504748f. Phiên bản mới: versionCode 35, 0.7.19-Gecko.

## Kết luận từ mã 0.7.18

1. resolveTargetIndex nhận Book từ snapshot danh sách hoặc callback quay lại. Snapshot có thể vẫn giữ chương 1 dù database đã lưu chương mới nhất. Hàm ưu tiên current của snapshot nên mở lại chương cũ. Bản mới luôn lấy vị trí mới nhất từ Store.
2. illustrationHint gồm cả chương ứng viên ít chữ và ảnh. 0.7.18 dùng hint để bỏ qua vị trí đang đọc và chương tiếp theo. Đây là nhầm giữa ưu tiên tải và bằng chứng chương ảnh. Bản mới chỉ bỏ qua minh họa đã xác nhận; chương ngắn/chưa xác định vẫn được giữ trong luồng đọc.
3. openChapter đặt pendingBook trước khi trình đọc lưu vị trí. Worker có thể lập phạm vi theo chương cũ. Bản mới lưu lựa chọn chương trước, rồi mới xét tải sau khi trình đọc có vị trí.
4. Mở chương gọi MODE_BOOK_NEXT, nhưng Repository gom cả chương cũ nếu truyện giữ full. Chỉ mở chương mới nhất vẫn có thể kích hoạt tải bù phần cũ. Bản mới có MODE_READER_NEXT riêng: từ vị trí đọc trở đi, giữ nguyên cờ full nhưng không tự tải bù phía trước. Cập nhật thư viện và yêu cầu Tải full vẫn kiểm tra toàn bộ theo lựa chọn đã có.
5. totalOfflinePercent ép tối đa 99 nếu chưa có sync_verified toàn thư viện. Bản mới tính phần trăm chữ theo cache thực tế; tình trạng cập nhật web báo riêng.

Đây là các đường gây lỗi xác định trong mã; không khẳng định mọi file cũ đều bị tải đè. Kiểm tra cache hiện có vẫn bỏ qua chương hợp lệ.

## Hành vi mới

- Đọc tiếp giữ chương đang đọc mới nhất, kể cả danh sách chưa được làm mới.
- Chương cuối không quay vòng về đầu. Minh họa đã xác nhận được bỏ qua khi chuyển chương.
- Nếu phần hiện tại và phía sau đã đủ, mở đọc không khởi động dịch vụ tải hoặc lấy lại mục lục.
- Tải do mở đọc không tải bù chương cũ; Tải full/Cập nhật vẫn hoạt động theo lựa chọn người dùng.
- Trạng thái Tạm dừng được giữ khi mở chương.
- Chương ứng viên minh họa có chữ không bị bỏ qua hoặc mặc định đã đọc.
- Cache chữ tốt, nguồn ảnh, thao tác sửa ảnh thiếu, giới hạn tải thử 15 chương và quy tắc sạc + Wi-Fi giữ nguyên.
- Package vn.nanase.hako, khóa ký, SQLite v3 và đường cache giữ nguyên.

## Kiểm tra và giới hạn

test.sh chạy toàn bộ kiểm tra JVM/JavaScript, gồm ReadingNavigationTest mới. ReadingStateTest chạy trực tiếp Store, MainActivity và Repository trên Android 11 với dữ liệu offline, kiểm tra snapshot cũ, chương cuối, không tải bù phần cũ, không ghi đè cache, giữ Pause/full, 100% chữ và cảnh báo ảnh riêng. ImageCacheTest kiểm tra decoder Android và cache ảnh hiện có.

Workflow chỉ xuất APK ARM32 sau khi các kiểm tra và build thành công. Kết quả cụ thể nằm trong artifact Hako-0.7.19-validation; báo cáo này mô tả tiêu chí, không tự coi một lượt chưa chạy là đã đạt. Chưa đo thực tế trên Xteink S4 hoặc kiểm tra phiên Hako của người dùng.
