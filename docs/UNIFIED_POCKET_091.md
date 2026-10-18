> Tài liệu lịch sử. Hướng dẫn hiện hành: [Pocket1.0](../README.md). Các mô tả Cloudflare/phiên bản cũ không áp dụng mặc định.

# Hako Pocket 0.9.1 — S4 dùng phím, K60 cuộn và đổi chương

## Phạm vi

Giữ package, chữ ký, database v3 và đường dẫn cache. Cài đè, không xóa dữ liệu.

- S4: trang chủ, chi tiết, menu và cài đặt dùng các ranh giới đo từ hàng hoặc dòng chữ. Hàng dài tiếp tục ở phần kế; trang cuối có khoảng trống thay vì kéo nửa hàng trước trở lại. Cài đặt có Trước/Sau và số trang.
- S4: danh sách/mục lục dùng chiều cao tự nhiên; tên không bị ép hai dòng. Phím đi qua các hàng đã đo; hàng vượt một màn dùng ranh giới dòng, lịch sử giữ cả offset. Không áp dụng thay đổi này lên bố cục K60.
- Hộp thoại phía trên nhận phím trước; phím âm lượng không tự xác nhận thao tác tải/thoát. Nút Chữ mở riêng hiển thị/thao tác đọc. Nội dung tải tự động bỏ khỏi bảng chữ.
- K60: khi cuộn, vuốt ngang rõ ràng từ vùng bên trong màn hình để đổi chương. Vuốt phải sang chương tiếp, trái về chương trước. Vuốt dọc, vùng sát mép Android và khóa cảm ứng không kích hoạt đổi chương.
- Mỗi chương giữ neo đọc riêng. Chuyển máy đồng bộ chương + đoạn + tỷ lệ trong đoạn + trích dẫn, không đồng bộ số trang. S4 mở trang chứa neo, có thể hiện lại vài dòng trước neo.
- Chi tiết: thao tác Đọc/Mục lục/Yêu thích/Tải tiếp, tag trước giới thiệu; giữ thông tin nguồn đầy đủ. Ảnh nhận cả asset server lẫn HTTPS trực tiếp, có fallback, cache giới hạn 8 MiB S4 / 32 MiB K60.
- S4 ưu tiên metadata đã lưu; người dùng chạm cập nhật khi cần. K60 giữ cập nhật nền. Metadata mới không thay nội dung đang xem trước khi người dùng chọn áp dụng.
- Checkpoint cục bộ theo đợt; đồng bộ nền S4 tối đa trì hoãn theo lịch 60 giây, K60 3 giây sau khi có checkpoint. Rời chương/đóng app yêu cầu gửi ngay; mất mạng giữ hàng chờ. Không có polling liên tục khi không có tiến độ mới.
- Portable locator bổ sung thời điểm đọc và lượt mở để hai Pocket mới có thể nhận trạng thái lịch sử. Lượt mở hợp nhất bằng giá trị lớn nhất; không tuyên bố cộng chính xác các lượt mở đồng thời ở hai máy.

## Đồng bộ chỉ qua Ubuntu

Theo lựa chọn của chủ sở hữu, Pocket không đọc cấu hình hoặc gửi yêu cầu tới Cloudflare. Cấu hình relay trên Ubuntu đã tắt; giữ dữ liệu và liên kết hiện có. Rời chương gửi vị trí nhỏ trước, metadata và số chương theo sau. Khi Ubuntu tắt, từng máy đọc cache và giữ tiến độ riêng; tiến độ mới chưa chuyển sang máy kia cho tới khi Ubuntu hoạt động lại.

## Kiểm tra phát hành

Bản Ubuntu-only đã qua CI: 14 nhóm kiểm tra Android, gồm 82 kiểm tra phân trang/thao tác. Cài đè 0.9.1 lên S4 và K60 thành công, giữ dữ liệu. Đã kiểm tra ảnh bìa thật trên K60, tag/thao tác trong chi tiết, cuộn dọc, vuốt đổi chương, vị trí K60 → S4 và S4 → K60 qua Ubuntu. S4 mở cache khi tắt Wi-Fi, sau bật lại đã gửi vị trí mới lên Ubuntu.

Kiểm tra vật lý phát hiện màn cài đặt S4 bị bọc hai pager. Đã sửa và thêm kiểm tra đúng một pager trong màn thật; APK sửa cuối đã qua CI và cài đè cả hai máy. Thử thật trên S4: giảm âm lượng sang trang 2, tăng âm lượng trở lại trang 1; không còn pager lồng nhau. Chưa đo mức tiết kiệm pin hoặc khẳng định mọi giao diện đều tối ưu. Không kiểm tra gửi thao tác ghi lên tài khoản Hako thật trong lượt này.


## Bản phát hành đã công bố

- CI cuối: https://github.com/nanasekoto/HakoReaderII/actions/runs/39074625607 — thành công.
- Mã app: 3735041443bb6a285365feb31a3ffeb86f8db4fe; 0.9.1 / code 64.
- SHA256 APK: `9bc65f855b20d8c6d239cb1ec2e1dd3bcada5841d759128d9b0adcf7405bece9`.
- Tải: https://nanains.duckdns.org/hako/download/pocket.apk; đã tải lại qua HTTPS và đối chiếu hash.
- Backup đầy đủ sau công bố: HDD 3 TB, `hako-full-20261017-222752-cb1c9a.tar` (804874240 byte).
- Kết quả pin và thời gian mở chương chưa được đo định lượng; không tuyên bố toàn bộ lỗi ảnh/giao diện của mọi truyện đã hết.
