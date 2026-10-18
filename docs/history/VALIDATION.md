# Kiểm chứng và giới hạn

## Phiên bản nền

Source 0.7.15 Gecko: commit aed36f73bd4d3d13fb8ab0de4e0c77e4098aa3fb.

[GitHub Actions thành công](https://github.com/nanasekoto/HakoReaderII/actions/runs/37932154389) kiểm tra các policy tải/cache/hàng đợi/neo/phân trang, parser, hồi quy và hợp nhất mục lục/đọc hết, rồi build release ARM32. APK đã được kiểm tra chữ ký phù hợp bản trước. Người dùng phản hồi phiên bản này khá ổn trên thiết bị; phản hồi này không thay thế phép đo từng chức năng.

## Kiểm tra bản hợp nhất

[Run hợp nhất thành công](https://github.com/nanasekoto/HakoReaderII/actions/runs/37935805438), commit d9b9f634a524ac6accadeea5ea16d48862092321: toàn bộ 12 lớp test JVM, kiểm tra JS, release Android, chữ ký và 13 thư viện ELF32 ARM đã đạt. Root version được áp dụng trong đường build mới. Chỉnh sửa sau run này giới hạn ở tài liệu, cấu hình trigger/kiểm tra metadata CI và khóa mục tiêu script emulator; main chạy lại đường build chuẩn sau hợp nhất.

## Bộ test hiện hành

bash test.sh chạy tất cả test JVM ở tests/*.java, cùng kiểm tra JavaScript:

| Nhóm | Test |
| --- | --- |
| Parser, nội dung lỗi, bảo vệ HTML | ParserTest |
| Phân trang, vị trí, quy tắc hồi quy | FixTest |
| Thời gian, khóa chương, thứ tự kệ | ShelfFeatureTest, ShelfPagePolicyTest |
| Cache chữ, ảnh và hàng đợi duy nhất | CachePolicyTest, DownloadLedgerTest |
| Phạm vi truyện/chương và ưu tiên | DownloadScopeTest, ReadingRangeTest, DownloadPriorityTest |
| Điều kiện dừng theo neo | ShelfAnchorTest |
| Giữ phím | HoldKeyTest |
| Catalog hợp nhất, ID lỗi, điều kiện đọc hết | PolicyIntegrityTest |

build.yml bổ sung biên dịch Android, đóng gói release, chữ ký APK, ABI/ELF32 ARM và checksum. Không gọi kết quả build là test toàn bộ trên máy thật.

## Kiểm chứng Android trước đây

[Run emulator lịch sử 0.7.0](https://github.com/nanasekoto/HakoReaderII/actions/runs/37504472355) đã kiểm tra Android 11/API30 ở 480×800 và DPI219/220, reader native, IPC, DOM chương tổng hợp xuất hiện trễ, cookie qua đóng/mở engine và thoát tiến trình. Các kết quả đó chỉ áp dụng source của run, không tự chuyển thành bằng chứng emulator cho 0.7.15.

tests/android và workflow emulator thủ công được giữ để kiểm tra tiếp. Trang web fixture tổng hợp không chứng minh challenge Hako thực tế.

## Cần kiểm tra trên S4 khi thay đổi liên quan

- Cài đè vẫn giữ bộ nghìn chương, vị trí đọc và dùng chung cache giữa các danh sách.
- Truyện chưa đọc trên APK không vào hàng tải chỉ vì có trên web.
- Phạm vi thường 15 chương, theo dõi/yêu thích đã đọc tải đến cuối, full giữ đủ.
- Neo không bỏ sót cập nhật khi đọc/mark-read làm thay đổi thứ tự kệ.
- Hoàn thành có chương thêm không tự bị xác nhận đã đọc.
- Bố cục không nhảy, không cắt góc/nhãn ở font người dùng; nút phản hồi kèm trạng thái.
- ReadSync bận thì lệnh Cập nhật vẫn được tiếp tục; pause/resume đúng.
- Đăng nhập, phiên Gecko, tốc độ, pin/RAM và e-ink/ánh xạ phím thật.

Khoảng cách giữa xác minh mục lục và mark-read server vẫn là giới hạn không nguyên tử. Hako có thể đổi markup hoặc yêu cầu xác minh mới. Không cam kết “không lỗi”, mức tiết kiệm pin hay tỷ lệ challenge thành công nếu chưa đo.
