# Hướng dẫn Hako Pocket1.0

## Cài và cập nhật

1. K60 mở `https://nanains.duckdns.org/hako/download`, hoặc quét QR ở trang đó.
2. Tải Pocket APK, mở file, cho phép trình duyệt cài ứng dụng nếu Android hỏi. Cùng APK ARM32 phục vụ S4 và K60 hiện tại.
3. Cài đè, không gỡ Pocket hoặc xóa bộ nhớ app. Nếu khác chữ ký, dừng và lấy đúng bản ký; gỡ app sẽ mất dữ liệu riêng trên máy.
4. Xem phiên bản trong thông tin ứng dụng Android. Hako Kiểm thử là app riêng, không phải bản dùng hằng ngày.

## Liên kết hai máy

1. Mở `https://nanains.duckdns.org/hako`, đăng nhập quản lý.
2. Đăng nhập/xác minh Hako trong trình duyệt server khi nguồn yêu cầu.
3. Tạo mã mới. Trong Pocket: Cài đặt → Kết nối & nguồn nội dung → Liên kết / trạng thái đồng bộ. Nhập đầy đủ địa chỉ HTTPS có `/hako` và mã.
4. Chấp thuận thiết bị ở trang quản lý nếu đang chờ. Mỗi máy token riêng; thu hồi tại đây nếu mất máy.
5. Làm tương tự cho máy còn lại. Mã có hạn/dùng một lần; không chia sẻ mật khẩu, token hoặc cookie.

Phiên Hako trên Ubuntu phục vụ chế độ server. Phiên trực tiếp của từng điện thoại chỉ dùng khi chọn nguồn web trực tiếp.

## Các danh sách

| Mục | Ý nghĩa |
| --- | --- |
| Đọc tiếp | Vị trí mới nhất đã nhận; có thể phải lấy metadata/file nếu máy chưa có |
| Vừa đọc | Lịch sử mở đọc |
| Yêu thích | Truyện ghim, không tự có nghĩa tải full |
| Tủ sách | Theo dõi trên Hako; nhập danh sách không tải mọi truyện |
| Mới cập nhật/Khám phá | Nội dung cập nhật hoặc truyện mới theo bộ lọc |
| Đã tải xong | Trạng thái hoàn thành và đủ chữ theo mục lục đã biết; cần xem nơi lưu |

Đã đọc, đã tải và Hoàn thành khác nhau. Metadata nơi khác không chứng minh điện thoại có file. Xem nhãn Máy này/Nơi khác trước khi đọc offline.

## Đọc và chuyển máy

- S4: volume lật trang/phân màn; giữ khoảng700ms khóa/mở cảm ứng. Phím nguồn thuộc hệ thống.
- K60: chọn cuộn hoặc phân trang trong thiết lập đọc; vuốt ngang chuyển chương theo cấu hình. Công cụ đọc có mục lục, font, tìm nội dung, đánh dấu và ảnh.
- Font/thao tác đọc tách khỏi cài đặt tải/nguồn của app.
- Chuyển máy: rời chương → chờ **Đã gửi vị trí** → máy kia bấm **Đọc tiếp**. Đồng bộ đoạn/ký tự, không phải số trang vật lý.
- Ubuntu tắt: đọc nội dung đã lưu và giữ tiến độ trên máy. Máy kia chưa thể biết phần chưa gửi; đợi server trở lại.

## Tải và cache

Tự tải theo đợt tối đa15 chương chữ gồm chương bắt đầu; tới chương chữ14 xét đợt kế. Minh họa không tính vào đợt chữ. Tải full là yêu cầu riêng. Pause giữ ý định tải; Stop khác xóa file.

Thoát chương không xóa ngay truyện đọc thử. Cache dọn theo tuổi/ngân sách và điều kiện bảo vệ. Không dọn cache để sửa đồng bộ. Xóa tải vẫn giữ metadata, lịch sử và vị trí; xác nhận đúng truyện trước khi xóa.

Nội dung tải trực tiếp có hàng upload riêng. **Đã gửi vị trí không có nghĩa mọi chương/ảnh đã sao lưu**; xem trạng thái nội dung riêng trong kết nối đồng bộ.

## Nguồn và xử lý lỗi

Trong Kết nối & nguồn nội dung chọn Tự động / Chỉ server / Hako trực tiếp. Địa chỉ server có `/hako`. Host Hako nhập thủ công phải là HTTPS hợp lệ; cookie riêng từng origin.

| Hiện tượng | Xử lý |
| --- | --- |
| Server không kết nối | Thử `/hako/health`, kiểm tra mạng/Ubuntu; chuyển nguồn thủ công nếu cần |
| Nguồn yêu cầu đăng nhập | Chế độ server xử lý trên Ubuntu; trực tiếp xử lý trên máy đó |
| Ảnh K60 thiếu | Sửa/tải ảnh riêng; không xóa chữ tốt để tải lại cả chương |
| Đồng bộ đang chờ | Chưa phải thành công; giữ mạng và kiểm tra khi server trở lại |
| Máy kia chưa thấy mới | Xác nhận máy gửi đã ACK, máy nhận dùng đúng server và đã làm mới |

## Báo lỗi và bản kiểm thử

Báo cáo ở Home, có điều khiển thu log. Tắt log khi không cần chẩn đoán; vị trí và đồng bộ vẫn được lưu. Chỉ gửi báo cáo chủ động, kiểm tra nội dung trước chia sẻ.

Gỡ Hako Kiểm thử và runner sau nghiệm thu, không gỡ Pocket. Công cụ dọn QA trong `scripts/` chỉ được nhắm `vn.nanase.hako.qa*`, tuyệt đối không xóa dữ liệu app thật.
