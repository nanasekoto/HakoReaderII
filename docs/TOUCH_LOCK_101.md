# Khôi phục khóa cảm ứng — 1.0.1

## Lỗi xác nhận trên S4 với 1.0.0

Home không nhận chạm. Giữ Volume Up 1100ms được Android nhận nhưng không đi vào nhánh giữ mở khóa. Tiêm phím Page Up cùng thời lượng qua adb đi vào nhánh và log ghi `locked=true` → timer → `locked=false`. Sau đó chạm Cài đặt hoạt động; đóng/mở app rồi chạm lại vẫn hoạt động. Không cần xóa dữ liệu.

Khóa được lưu trong preferences và chặn touch toàn Activity. Nhánh volume khi ngoài reader chạy trước nhánh giữ khóa, vì vậy khóa còn lưu ở Home không mở bằng volume được. Nhánh tắt volume lật trang trên phone cũng có thể che mất đường thoát này.

## Thay đổi

Ưu tiên Page Up/Volume Up khi đang khóa hoặc gesture giữ đang hoạt động, trước list/settings/volume preference. Giữ UP đi cùng DOWN kể cả timer đã mở khóa; thả không lật trang và reset trạng thái để lần giữ kế tiếp hoạt động. Không thay nội dung, tiến độ, schema hoặc chữ ký.

## Kiểm tra

Thêm ca QA: khóa tại Home, khởi động lại, giữ volume mở khóa, lặp lại lần hai; mở khóa trong reader khi volume lật trang tắt. Ca chạy bằng Activity thật trong fixture, tách khỏi dữ liệu production. Kết quả APK/emulator/S4 của bản1.0.1 ghi trong báo cáo lượt build, không suy từ việc biên dịch hoặc từ lần mở khóa thủ công1.0.0.

## Kết quả APK1.0.1


- CI39311031157 thành công, build commit4ff967e: Android regression và4 cấu hình fixture. S4 font1.0/1.3 mỗi lượt101 kiểm tra; phone mỗi lượt77.
- QA S4 thật:101 kiểm tra,27 ảnh; không ca thất bại. Có ca Home khởi tạo lại khi khóa, lặp giữ/thả và reader tắt volume lật trang.
- Pocket thật trên S4:3 vòng và cold restart nêu trên đạt. K60 chỉ kiểm giả lập trong lượt này.
- APK cùng chứng chỉ; SHA256: `016d9ddf8afd3c8f384df88b2713b057cf51ed66df29c6a46112b8a116daf32e`.

Phạm vi kết luận là lỗi khóa cảm ứng đã quan sát và các ca hồi quy đã chạy. Các mục nghiệm thu toàn sản phẩm còn mở ở mốc1.0 không được chuyển thành đạt. Không thay backend/schema/cache; không cần gỡ Pocket để cập nhật.

Tải APK tại Release hoặc https://nanains.duckdns.org/hako/download. Giữ Volume Up khoảng1giây để mở khóa khi cần. Không gỡ app/xóa dữ liệu để xử lý khóa.
