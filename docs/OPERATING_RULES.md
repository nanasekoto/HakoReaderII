# Quy tắc vận hành hiện hành

Quy tắc nền 0.7.15 và bản vá chương ảnh 0.7.16. Đây là quy tắc đã chốt với người dùng; tài liệu lịch sử không được dùng để thay thế bảng dưới.

## 1. Phân biệt các danh sách

| Mục | Ý nghĩa |
| --- | --- |
| Tủ sách | Các truyện theo dõi trên Hako; cập nhật thông tin không có nghĩa tải toàn bộ |
| Vừa đọc | Lịch sử đọc; có điều khiển tải full và đánh dấu Hoàn thành |
| Yêu thích | Truyện ghim hoặc thường đọc; sắp xếp đọc nhiều trước, ghim để dễ tìm |
| Mới cập nhật | Truyện có dấu hiệu cập nhật theo thông tin tủ sách/mục lục |
| Đã tải xong | Người dùng đánh dấu Hoàn thành, đủ chữ offline, không còn thay đổi mục lục chưa xử lý |

Mọi mục dùng chung Store và cache chương theo ID. Chuyển danh sách không tạo thư viện tải riêng. “Đã tải xong”, “đã đọc hết”, “Hoàn thành” và “chương mới” là các trạng thái khác nhau.

## 2. Phạm vi tải và giữ

| Trường hợp | Phạm vi tải | Giữ dữ liệu |
| --- | --- | --- |
| Chỉ có trên kệ Hako, chưa đọc trong APK | Không tự tải chỉ vì xuất hiện trên kệ | Giữ dữ liệu đã có theo chính sách hiện hành |
| Đã đọc trong APK, chưa theo dõi/yêu thích | Chương hiện tại đến current + 15, gồm hai đầu | Giữ ba chương trước và các chương từ hiện tại trở đi |
| Theo dõi/yêu thích và đã đọc trong APK | Chương hiện tại đến cuối mục lục | Giữ ba chương trước và các chương từ hiện tại trở đi |
| Tải full hoặc tự đánh dấu Hoàn thành | Toàn bộ mục lục | Giữ toàn bộ |
| Chủ động bật tải tự động cho truyện | Truyện được xét tải theo lựa chọn rõ ràng này | Áp dụng phạm vi tương ứng của truyện |

“Đã đọc trong APK” dùng lượt đọc thực tế, không suy ra từ thời gian nhập truyện trên web. Theo dõi/ghim đơn độc không đủ để tự tải cả bộ. Giữ ba chương trước không có nghĩa phải tải lại ba chương đã thiếu. Chỉ yêu cầu tải lại rõ ràng mới được bỏ qua quy tắc dùng cache hợp lệ.

Ưu tiên tải các truyện **đã đủ điều kiện**:

1. Có cập nhật/chương mới.
2. Thường đọc, từ ba lượt truy cập.
3. Các truyện chưa hoàn thành còn lại.
4. Hoàn thành và ít đọc.

Cập nhật thông tin tủ sách diễn ra trước tải chương. Hoàn thành không tự đưa truyện lên ưu tiên đầu; yêu thích không đồng nghĩa luôn ưu tiên cao.

## 3. Cập nhật tủ sách

- Khi khởi động, giao diện nạp dữ liệu cục bộ và chuẩn bị snapshot trên worker. Các tác vụ tự động vẫn tuân thủ điều kiện mạng, sạc và tạm dừng; không đồng nhất mở app với ép quét web đầy đủ.
- Cập nhật thủ công và tự động dùng cùng nguyên tắc dừng sớm.
- Neo là truyện cập nhật chưa đọc cũ nhất đã biết, có ID và khóa chương mới nhất. Dừng chỉ khi gặp neo không đổi, thứ tự trang an toàn và neo đã được đối chiếu.
- Chưa có neo, neo đổi, dữ liệu không rõ, thứ tự không đáng tin hoặc lần đối chiếu trước lệch: quét đầy đủ.
- Quét đối chiếu định kỳ sau 24 giờ kiểm tra việc dừng sớm; nếu có thể bỏ sót thì vô hiệu hóa quét nhanh. Một lần đối chiếu đạt điều kiện có thể khôi phục.
- Khóa chương mới nhất không đổi và đã có mục lục hợp lệ, không có lỗi trước đó: bỏ qua request mục lục. Truyện mới, khóa rỗng, lỗi trước, yêu cầu làm mới hoặc xác minh đọc hết: lấy lại mục lục.
- Bỏ qua mục lục không bỏ qua việc tải tiếp các chương còn thiếu trong phạm vi cho phép.
- Chỉ ghi thời gian đồng bộ thành công sau khi lượt nhập tủ sách hoàn tất. Lượt dở có mốc riêng; không giả báo thành công.

App dựa trên thông tin Hako thực tế trả về. Không suy ra “trang sau chắc không có cập nhật” chỉ từ số truyện ít, tên truyện hoặc ngày parse thất bại.

## 4. Đọc hết và Hoàn thành

Hoàn thành do người dùng đánh dấu trong Vừa đọc. Nó bật lưu full; không tự suy ra tác giả đã dịch xong vì người dùng đọc đến cuối. Khi có chương/spin-off mới trong mục lục, vẫn tiếp nhận và tải phần thiếu; các chương chưa đọc không trở thành đã đọc chỉ vì tải xong.

Xác nhận đọc hết trên Hako cần:

- Snapshot ID các chương đã đọc offline được lưu cục bộ.
- Lấy mục lục server mới để xác minh.
- Mục lục server không rỗng, ID hợp lệ và không trùng.
- Mọi ID server đều có trong phần đã đọc và snapshot hàng chờ.
- Không có trạng thái mục lục bị ẩn/không rõ gây nghi ngờ.

Chỉ đến cuối phần đã tải, ví dụ chương 70 trong bộ 100 chương, không đủ điều kiện. Thiếu bằng chứng thì giữ yêu cầu chờ và báo lý do. Thao tác Hako đánh dấu toàn bộ theo trạng thái server; khoảng cách giữa xác minh và gửi lệnh không thể bảo đảm nguyên tử khi server không cung cấp thao tác có điều kiện.

## 5. Cache và an toàn dữ liệu

- Chương có chữ thật hợp lệ được coi là đọc offline được, kể cả thiếu minh họa. Chương chỉ có ảnh cần đủ file ảnh local giải mã được; thông báo lỗi ảnh không phải nội dung. Xem [bản vá 0.7.16](IMAGE_CHAPTER_FIX_0716.md).
- File không rỗng chưa đủ: phải nhận diện nội dung truyện, loại trang lỗi và challenge.
- Cùng ID chương dùng cùng file; hàng đợi không trùng ID và kiểm tra cache trước request.
- Mục lục thay đổi không được xóa chương cũ hoặc vị trí đọc. Chương cũ thiếu trên web được giữ và đánh dấu ẩn, không tự tải lại.
- Không gỡ app/xóa dữ liệu để giải quyết lỗi. Cài đè cùng chữ ký và package giữ dữ liệu; hạ phiên bản không được coi là phương án luôn khả dụng.

## 6. Trải nghiệm quan sát được

Giao diện báo giai đoạn: chuẩn bị cache, quét trang, kiểm tra/bỏ qua mục lục, tải chương, nghỉ và lỗi. Nêu lý do bỏ qua/chờ; không để người dùng suy đoán nút không phản hồi.

Vùng trạng thái dành sẵn chỗ, không thêm/bỏ gạch chân làm thẻ nhảy. Không chen thẻ ra ngoài màn hình hoặc cắt góc bo tròn. Khoảng sáu thẻ ở cỡ chữ mặc định; giảm số thẻ nếu font lớn hoặc vùng nhìn hẹp. Giữ tên dài để xem đầy đủ.

Tạm dừng/Tiếp tục điều khiển đồng bộ. Mở Tài khoản dừng tải nền để đăng nhập; đọc offline không cần engine chạy liên tục.
