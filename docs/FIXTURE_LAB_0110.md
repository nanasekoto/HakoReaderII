> Tài liệu lịch sử. Hướng dẫn hiện hành: [Pocket1.0](../README.md). Các mô tả Cloudflare/phiên bản cũ không áp dụng mặc định.

# Pocket 0.11.0 — kiểm thử theo đợt

## Hai ứng dụng, hai kho
Pocket giữ package vn.nanase.hako, chữ ký và schema3. APK lab có package vn.nanase.hako.qa, nhãn Hako Kiểm thử, không có quyền Internet. Không cài instrumentation vào Pocket thật. FixtureLabActivity/FixtureLibrary và JSON mẫu chỉ nằm trong source set QA, không có trong release Pocket.

Lab launcher có Tạo mẫu S4/K60, Mở app và Xóa dữ liệu kiểm thử. Nút xóa kiểm tra đúng package rồi dùng Android clearApplicationUserData, chỉ xóa sandbox QA. scripts/cleanup-lab.py --serial SERIAL chỉ gỡ vn.nanase.hako.qa.tests và vn.nanase.hako.qa; không chấp nhận tên package khác. Không xóa dữ liệu server thật.

Bộ JSON giống hệt ở server tests/fixtures: bốn truyện, 1.563 chương metadata (20+1500+35+8), chữ Việt, tên/tag/giới thiệu dài, nhóm tập, lịch sử/yêu thích/hoàn thành, đủ/thiếu cache, ảnh dọc/ngang/thiếu, chương minh họa và thông báo ngắn. Hình tạo cục bộ bằng Canvas, không cần Hako/CDN. Nguồn được đặt chỉ server với domain example.invalid, không token; permission Internet bị loại tại manifest.

## Sửa cùng đợt
- Home dùng chiều cao tự nhiên cho hàng và tên mục, tránh font lớn bị ép hai dòng.
- Cài đặt font: tên thông số riêng, nút −/+ 48dp, giá trị có vùng co giãn; footer panel không ép nút vào 40dp.
- Detail K60 xếp bìa/tên thành cột khi vùng ngang hẹp hoặc fontScale>1.25; danh sách cá nhân không ép trạng thái và badge vào một hàng ở S4/chữ lớn.
- Nhớ vị trí danh sách cá nhân khi quay lại; giữ snapshot và danh sách rỗng/offline có giải thích, không mở browser khi đã biết mất mạng.
- S4 menu không có sửa ảnh; list trong dialog có mask hàng chưa trọn và volume dùng cùng list thật.
- Tìm trong chương đang mở có lối vào menu đọc, không lấy thêm chương. Nhảy tới kết quả theo layout hiện tại, không dùng số trang máy khác.

## Một pipeline sau đóng băng mã
Các gate cũ vẫn chạy. Sau đó cùng code dựng QA x86_64 một lần và chạy FixtureJourneyTest trên Android11 480x800/density220 và Android14 1080x2400/density420, fontScale1.0/1.3. Mỗi ca ghi kết quả và ảnh; gom lỗi hết các ca rồi mới kết luận. Server chạy HTTP API thật trong TestClient với fake nguồn và TemporaryDirectory, hai thiết bị giả, ACK/duplicate/latest/backwards/cache/pack/restart/revoke, dọn toàn thư mục khi kết thúc. Chỉ khi gate đạt mới đóng QA ARM32 và Pocket release ARM32 cùng lượt.

APK cuối được nghiệm thu trên S4/K60 bằng kho lab. Lưu bằng chứng rồi gỡ QA. Chỉ sau nghiệm thu mới cập nhật link Pocket thật. Một build không được xem là cam kết tuyệt đối không lỗi: nếu còn lỗi, báo cả danh sách và giữ bản đang dùng; không xuất bản một APK chưa đạt.

## Giới hạn bằng chứng
Fixture không chứng minh login/captcha/HTML Hako đang chạy, pin e-ink hay mạng Internet. Không thêm chức năng upload file local ngược server trong batch này; trạng thái cache khác bản sao file. Tự dọn cache theo TTL mới vẫn chưa bật. Không gọi tổng số case là bằng chứng mọi yêu cầu đã đạt; xem biên bản riêng sau pipeline.

## Bản đã kiểm và phát hành
CI39114897466 thành công ở commit d3ff584. Server75pytest; 14 suite Android cũ; bốn cấu hình fixture đạt; S4 thật45/K60 thật34 kiểm đạt. Code69 đã cài đè hai máy và công bố /hako/download/pocket.apk, SHA256 d25780c1dcb308c1f4173e17eb48babda49155d54131d494d9fc0de23f90336a. Đã thử nút xóa kho QA trên S4, gỡ cả hai gói QA ở hai máy. Một số ảnh tự động quá sớm bị loại khỏi bằng chứng thị giác; có ảnh máy thật bổ sung sau khi màn đã ổn định.
