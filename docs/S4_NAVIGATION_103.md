# Hako Pocket 1.0.3 · phản hồi ảnh S4 ngày 21/10/2026

Phạm vi: 8 ảnh 00:41–00:50, bản 1.0.2 đã cài trên S4.

## Mới cập nhật / Khám phá

- Nút mũi tên vòng làm mới đặt góc phải thanh tiêu đề, có vùng bấm 48dp.
- “Bản đã lưu” là nhãn trạng thái; không yêu cầu bấm vào dòng chữ để cập nhật.
- Bấm tựa truyện: dòng trạng thái đổi thành “Đang lấy thông tin…”, giữ danh sách hiện tại. Đọc cache chi tiết/mục lục trước, chỉ lấy nguồn khi thiếu. Sau khi sẵn sàng mới chuyển sang trang chi tiết một lần; không dựng trang trống rồi dựng lại.
- Nếu chưa lấy được dữ liệu, giữ danh sách và thông báo thử lại. Callback cũ không chuyển màn khi người dùng đã rời danh sách.
- S4 dùng bản đã lưu đến khi người dùng bấm làm mới; tránh tự đánh thức mạng để làm mới cache đã có.

## Chi tiết S4

- Tên 22sp, chữ thông tin 15sp, tag 14sp; thống kê giữ cấu trúc nhãn/giá trị và giá trị 16sp.
- Phần đầu dùng khoảng trống màn hình để giãn giữa các khối. Tên dài/tag nhiều tăng chiều cao tự nhiên, không cắt nội dung hay cố định số dòng.
- Giới thiệu dùng cùng bộ chọn font, cỡ chữ, đậm và giãn dòng của trình đọc. Phân màn theo dòng khi nội dung dài.
- Bỏ nút “Làm mới thông tin”.

## Phân màn danh sách

Nguyên nhân khung truyện trước còn sót: ListView bị giới hạn bởi đáy danh sách, tự kéo phần trước lên lấp phần trống khi setSelectionFromTop tại trang cuối.

Sửa bằng vùng trắng cuối danh sách S4 cao bằng vùng nhìn. Vùng này không phải truyện, không được đếm hoặc mở. Lật theo các hàng đo được và lịch sử điểm đầu trang, không lật vào một trang trắng. Trang cuối giữ các truyện còn lại từ trên xuống; không đưa truyện trang trước vào lấp. Vuốt S4 chuyển theo màn, không thả cuộn tự do làm lệch điểm bắt đầu. K60 giữ cuộn cảm ứng.

Vừa đọc/Yêu thích/Tủ sách có dòng nhỏ bên dưới: trang hiện tại, dải truyện/tổng số và “Cuối” khi hết. Số truyện một màn theo chiều cao thật của tên/nút; không ép mọi màn cùng số truyện khi hàng cao khác nhau.

## Phạm vi xác nhận

Theo yêu cầu chủ sở hữu: đọc ảnh/mã, gom sửa, build APK rồi cài đè S4. **Không chạy test, giả lập hoặc duyệt thử các màn.** Kết quả build/cài đặt không thay thế việc chủ sở hữu kiểm tra hiển thị. Giữ package, chữ ký, dữ liệu và cache hiện hành.
