# Bản vá chương chỉ có ảnh — 0.7.16 Gecko

## Bằng chứng trên main 0.7.15

imageAllowed thiếu docln.net/hako.re; sanitizer chỉ cho HTTPS. Repository đã dùng readable để bỏ qua cache, ghi ready=true cho chữ và Store.progress đã đếm readable. Vì vậy các đoạn ready/queue trong chẩn đoán bàn giao thuộc bản cũ, không được áp dụng lại nguyên xi.

Ảnh bị thay bằng thông báo: chương chỉ có ảnh có thể mất nội dung thật nhưng được coi là có chữ; ảnh HTTP bị sanitizer loại src có thể làm chương rỗng và tiến độ thiếu. Không được ép 100% cho HTML không có ảnh offline.

## Thay đổi

- Nhận đúng host gốc/subdomain docln.net, hako.re, hako.vip, hako.vn và origin; chặn host giả, protocol lạ và URL có credentials.
- Giữ ảnh HTTP qua sanitizer và nâng URL Hako được phép sang HTTPS trước tải; không mở cleartext cho toàn ứng dụng.
- Chỉ lưu ảnh thành công khi Android decoder nhận kích thước hợp lệ; trang HTML lỗi không được lưu như ảnh.
- Chương có chữ thật: thiếu ảnh là cảnh báo, không tải lại chữ.
- Chương chỉ có ảnh: mọi ảnh cần có file local giải mã được; ảnh thiếu/hỏng không tính hoàn tất.
- Giữ markup và nguồn ảnh bị lỗi. Lượt sau thử ảnh thiếu từ cache, tái dùng ảnh đã có, không request lại trang chương nếu đã giữ đủ nguồn ảnh.
- Cache cũ chỉ có thông báo lỗi ảnh: nhận diện không phải nội dung, lấy lại đúng chương bị ảnh hưởng. Chương cũ còn ảnh hợp lệ không tải lại.
- Fingerprint cache chương ảnh bao gồm các file ảnh, để phát hiện ảnh bị mất/đổi.
- Không đổi SQLite v3, package, khóa ký hoặc vị trí đọc.

## Kiểm tra

ParserTest thêm domain thật/giả, HTTP, sanitizer, chương toàn ảnh, thiếu một ảnh, cache placeholder, chữ kèm ảnh lỗi và trang lỗi. ImageCacheTest kiểm tra SQLite + BitmapFactory trên Android 11 với PNG thật, progress 100/67/75, ảnh bị xóa/hỏng, thêm chương và bỏ qua cache cũ.

Build ARM32 và kiểm tra chữ ký/metadata qua workflow build. Workflow image-cache kiểm tra offline trên emulator, không cần tài khoản Hako. Kết quả CI không chứng minh CDN đang hoạt động hoặc mọi challenge thành công trên S4 thật.

## Cài đè và kết quả mong đợi

Cài đè cùng chữ ký. Giữ các chương đã đọc được; chỉ chương ảnh bị thiếu/rỗng cần sửa. Nếu bản cũ đã xóa nguồn ảnh và thay bằng thông báo, lần đầu bản vá phải lấy lại trang của chương đó. Sau khi ảnh đủ, bộ full đạt 100%; khi thêm chương chỉ tải phần thiếu. Nếu CDN vẫn lỗi, báo thiếu ảnh thay vì giả báo hoàn tất.
