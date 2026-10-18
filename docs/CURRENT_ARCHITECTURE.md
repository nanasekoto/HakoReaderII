# Kiến trúc Pocket1.0

```text
S4/K60 → MainActivity → UI snapshot → Store → NativeReader
                       ├ ServerSource → Ubuntu → Hako/cache/HDD
                       ├ ServerBridge → vị trí/thư viện chung
                       ├ ContentUpload → manifest/chunks/hash/ACK
                       └ Trực tiếp: S4 Gecko / K60 System WebView
```

Store giữ SQLite schema3 và `files/chapters/<chapter-id>/content.html`. NativeReader bố cục theo màn hình/font từng máy. ContentAnchor khớp đoạn chữ, bỏ ảnh khỏi chỉ số logic. Locator nhận trước file được giữ để remap sau tải.

ServerBridge có lane vị trí nhỏ, outbox bền và revision/event để chống phản hồi cũ. BridgeJob thử lại theo mạng. ContentUpload riêng, chunk hữu hạn và ACK file riêng. CacheMaintenance không dọn khi rời chương.

Repository/DownloadService điều phối nguồn trực tiếp/phạm vi tải. ServerSource nhận list/detail/TOC/chương cấu trúc, pack và ảnh riêng. Policy tách phạm vi, ưu tiên, hợp nhất catalog và xác minh Đọc hết. Snapshot chuẩn bị ngoài UI thread.

S4 phân màn/phím cứng, K60 cuộn/ảnh. Toolbar là lớp phủ giữ neo, không reflow chữ. Repo Server giữ FastAPI/Chromium/SQLite/archive/quản trị; `android/` và `relay/` ở đó là client/tích hợp lịch sử, không phải app chính hoặc phụ thuộc bắt buộc1.0.
