# Pocket 0.10.0 — giao diện riêng và server tương tác

## Thay đổi
- S4 text-only xuyên suốt payload/pack, cover, sửa ảnh và reader; ảnh cũ không bị xóa.
- K60 giữ bìa màu đúng tỷ lệ; thông tin gọn, tags wrap, thống kê tự dàn hàng; các tập có thể mở tại trang chi tiết, chương được thêm từng đợt40 để không tạo hàng nghìn view cùng lúc.
- S4 chi tiết chia Tổng quan/Giới thiệu/Mục lục, pager đo nội dung. List/title dùng chiều cao tự nhiên và không ép cắt tên.
- Khám phá có hai thứ tự, lọc riêng giữ lựa chọn, tên/chương tách và có yêu thích/tải. Back phục hồi vị trí sau khi dữ liệu được nạp.
- Home dành hai dòng trạng thái vị trí theo font, icon gọn hơn; feedback thao tác không bị tải nền ghi đè tại detail.
- Rời reader không xóa cache thử; mặc định không tự prune. Tải chủ động và dữ liệu đọc dở được giữ. Cleaner TTL mặc định chưa bật, không áp con số giữ mới chưa chốt.
- Reader không bị dựng lại khi ảnh hoàn tất; ảnh được dùng ở lần mở sau. Giữ neo và cử chỉ đã có.
- Server mới có lane chương riêng, hai view tương tác, một batch và hai ảnh. Browser dùng lại tối đa4 trang đọc đồng thời, writer Hako chờ readers; bulk riêng nhịp2s, interactive không chịu nhịp đó. Request đang trùng dùng chung kết quả; chương nóng5s tối đa16 mục.
- Cold view chờ kết quả trên server tối đa15s trước khi trả trạng thái chờ; cache trả ngay. Chương đang chọn không chờ xong pack15.

## Giữ nguyên
Package/signing/schema3/cache IDs, checkpoint/neo, sync Ubuntu, phân biệt offline máy này/máy khác, 15/14, Pause/Stop/full và proof Đọc hết Hako. Không Cloudflare.

## Kiểm tra tại thời điểm chuẩn bị
JVM/parser/JS gates đã qua local; app và Android tests biên dịch. Server71pytest qua trước chỉnh cuối catalog lane; full suite sẽ được chạy cho mốc đóng gói. Chưa coi CI/thiết bị của0.10.0 đã đạt ở thời điểm viết. Physical layout, latency, live source và sync cần xác minh trên APK cuối.

## Giới hạn cần nói rõ
Không có tự dọn TTL mới khi người dùng chưa chốt; không mất data vì thoát. K60 ảnh mới hoàn tất không tự thay vào trang đang đọc để tránh nhảy vị trí. Không tuyên bố đã đo tiết kiệm pin hoặc mọi trường hợp web thật. Những chức năng cũ không thuộc batch này giữ cơ chế đã có, không gọi đây là rewrite toàn bộ sản phẩm.

## Hiệu chỉnh sau một lượt kiểm tra APK đủ chức năng

Bản code66 đã qua CI39104743094 và cài trên hai máy. Thử thật thấy Home lặp tiêu đề làm S4 mất hàng cuối, Đọc tiếp bị thống kê đẩy sang màn2; Explore K60 còn nhiều khoảng trống và footer cố định. Gom sửa thành code67: bỏ footer lặp, đưa hành động S4 lên trước thống kê, đưa bộ lọc K60 vào app bar, action nhỏ trong vùng48dp và footer kết quả ở cuối danh sách cuộn. Sửa range không tính hàng bị pager che và toggle yêu thích phản ánh đúng trạng thái. Không gọi code66 là bản hoàn thiện cuối.
