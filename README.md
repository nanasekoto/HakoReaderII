# Hako Pocket 1.0

Ứng dụng chính kế thừa **Hako Reader II**, dành cho **Xteink S4** và **Redmi K60**. Ubuntu lấy và chuẩn hóa nội dung; điện thoại giữ thư viện, file đọc và giao diện native offline.

**1.0.1 · code73 · Android 8+ · `vn.nanase.hako`**. Cài đè bằng cùng chữ ký để giữ dữ liệu.

- [Tải APK / QR](https://nanains.duckdns.org/hako/download) · [GitHub Releases](https://github.com/nanasekoto/HakoReaderII/releases)
- [Hướng dẫn sử dụng](docs/USER_GUIDE.md)
- [Quy tắc vận hành](docs/OPERATING_RULES.md) · [Kiến trúc](docs/CURRENT_ARCHITECTURE.md)
- [Build và bảo trì](docs/BUILDING.md) · [Kiểm chứng](docs/VALIDATION.md) · [Giới hạn](docs/KNOWN_LIMITATIONS.md)
- [Ubuntu companion](https://github.com/nanasekoto/HakoReaderServer) · [Thay đổi](CHANGELOG.md)

Bản vá1.0.1 sửa kẹt khóa cảm ứng khi Home còn lưu trạng thái khóa; [chi tiết và kiểm tra](docs/TOUCH_LOCK_101.md).

## Hai thiết bị

| | S4 | K60 |
| --- | --- | --- |
| Layout | Chữ, tương phản cao, phân màn | Cuộn cảm ứng, ảnh màu |
| Ảnh | Không yêu cầu/hiển thị trong e-ink | Bìa, minh họa, xem ảnh riêng |
| Đọc | Volume lật trang; giữ khoảng700ms khóa cảm ứng | Cuộn hoặc phân trang; vuốt ngang chuyển chương |
| Nguồn trực tiếp | Gecko riêng khi cần web | Android System WebView |
| Offline | Native từ file đã lưu | Native từ file đã lưu |

## Đồng bộ

Ubuntu là điểm đồng bộ duy nhất, **không cần Cloudflare**. Rời chương lưu vị trí local và ưu tiên gửi vị trí trước file. Chờ **Đã gửi vị trí** rồi chuyển máy. Đồng bộ chương và neo nội dung, không lấy số trang vật lý của máy này áp cho máy kia.

Thư viện chung metadata; nhãn Máy này/Nơi khác phân biệt file offline thật. Ubuntu tắt vẫn đọc file đã lưu; hai máy nhận tiến độ mới sau khi server trở lại. ACK vị trí khác ACK sao lưu nội dung.

## Mốc1.0

Chủ sở hữu chốt1.0 từ mã chức năng0.12.1. Tên phiên bản không thay thế bằng chứng nghiệm thu. [VALIDATION](docs/VALIDATION.md) ghi riêng kết quả mẫu, máy thật, nguồn thật và phần chưa kiểm đủ.

Tài liệu cũ giữ để truy vết. README và các liên kết hiện hành phía trên thay hướng dẫn Cloudflare/app Android Server riêng trong lịch sử.
