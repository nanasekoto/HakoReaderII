# Khôi phục khóa cảm ứng — 1.0.1

## Lỗi xác nhận trên S4 với 1.0.0

Home không nhận chạm. Giữ Volume Up 1100ms được Android nhận nhưng không đi vào nhánh giữ mở khóa. Tiêm phím Page Up cùng thời lượng qua adb đi vào nhánh và log ghi `locked=true` → timer → `locked=false`. Sau đó chạm Cài đặt hoạt động; đóng/mở app rồi chạm lại vẫn hoạt động. Không cần xóa dữ liệu.

Khóa được lưu trong preferences và chặn touch toàn Activity. Nhánh volume khi ngoài reader chạy trước nhánh giữ khóa, vì vậy khóa còn lưu ở Home không mở bằng volume được. Nhánh tắt volume lật trang trên phone cũng có thể che mất đường thoát này.

## Thay đổi

Ưu tiên Page Up/Volume Up khi đang khóa hoặc gesture giữ đang hoạt động, trước list/settings/volume preference. Giữ UP đi cùng DOWN kể cả timer đã mở khóa; thả không lật trang và reset trạng thái để lần giữ kế tiếp hoạt động. Không thay nội dung, tiến độ, schema hoặc chữ ký.

## Kiểm tra

Thêm ca QA: khóa tại Home, khởi động lại, giữ volume mở khóa, lặp lại lần hai; mở khóa trong reader khi volume lật trang tắt. Ca chạy bằng Activity thật trong fixture, tách khỏi dữ liệu production. Kết quả APK/emulator/S4 của bản1.0.1 ghi trong báo cáo lượt build, không suy từ việc biên dịch hoặc từ lần mở khóa thủ công1.0.0.
