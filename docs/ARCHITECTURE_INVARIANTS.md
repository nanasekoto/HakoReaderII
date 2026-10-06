# ARCHITECTURE INVARIANTS & RECURRING BUG PREVENTATIVE RULES (QUY TẮC BẤT BIẾN)

Tài liệu này lưu trữ toàn bộ các nguyên nhân gốc rễ và quy chuẩn bắt buộc của dự án **Hako Pocket** trên thiết bị E-ink **Xteink S4 (Android 11)**. Mọi lập trình viên hoặc AI Agent khi bảo trì dự án **BẮT BUỘC** phải tuân thủ nghiêm ngặt các quy tắc này để không bao giờ lặp lại các lỗi cố hữu.

---

## 1. QUY TẮC CƠ SỞ DỮ LIỆU SQLITE (NGĂN CHẶN CRASH NPE)
* **Nguyên nhân lỗi cũ:** Bảng SQLite `books` được định nghĩa với các cột `shelf_rank` và `shelf_info`. Khi tối ưu hàm `Store.book(id)`, câu lệnh SQL viết nhầm camelCase `shelfRank, shelfInfo`, khiến SQLite văng lỗi `no such column`, trả về `null` và gây sập ứng dụng (NullPointerException) trong `ChargeJob` và không thể mở chương đọc sách.
* **Quy tắc bắt buộc:**
  1. Toàn bộ các truy vấn `SELECT`, `UPDATE`, `INSERT` trong `Store.java` tới bảng `books` phải sử dụng chính xác tên cột chuẩn:
     * `shelf_rank` (KHÔNG ĐƯỢC dùng `shelfRank`)
     * `shelf_info` (KHÔNG ĐƯỢC dùng `shelfInfo`)
  2. Mọi truy vấn `book(id)` phải có kiểm tra an toàn `null-safe` trước khi truy cập thuộc tính `.id` hoặc gọi hàm con.
  3. Có bài kiểm thử tự động `tests/FixTest.java` khóa cứng điều kiện này.

---

## 2. QUY TẮC ĐIỀU HƯỚNG MÀN HÌNH & NÚT BACK (NGĂN CHẶN THOÁT NHẦM)
* **Nguyên nhân lỗi cũ:** 
  1. Icon ngôi nhà (`IconButton` kind 0) gọi lệnh `navStack.pop()` thay vì về trang chính, dẫn đến việc đang đọc sách lại nhảy vào "Chọn chương".
  2. Khi vào "Chọn chương", ngăn xếp `navStack` bị rỗng do màn hình trước đó chưa được lưu lại. Người dùng bấm Back thì bị kích hoạt `confirmExit()` đòi thoát app.
* **Quy tắc bắt buộc:**
  1. **Icon Ngôi nhà (Trang chủ):** Luôn luôn gọi trực tiếp `saveThen(() -> { leaveReader(); library(); })`. Tuyệt đối không gọi `navStack.pop()`. Bấm vào ngôi nhà là lưu tiến độ và về thẳng `library()`.
  2. **Ngăn xếp trước khi mở truyện:** Mọi thao tác bấm mở truyện từ Tủ sách (`bookList(shelf)`), Thường đọc (`frequentList()`), hay Tìm kiếm (`onlineList()`) đều phải gọi `navStack.push(...)` lưu lại màn hình hiện tại trước khi gọi `openBook(...)`.
  3. **Hàm `goBack()` an toàn tuyệt đối:**
     * Nếu `isBoundaryPromptVisible()` $\rightarrow$ đóng thông báo.
     * Nếu `nativeReader` đang mở toolbar $\rightarrow$ đóng toolbar.
     * Nếu đang đọc sách $\rightarrow$ lùi về ngăn xếp `navStack`, nếu rỗng thì về Tủ sách / Trang chính.
     * Nếu đang ở Mục lục / Chọn chương (`!bookId.isEmpty()`) mà ngăn xếp rỗng $\rightarrow$ lùi về Tủ sách (`bookList(true)`).
     * **Chỉ duy nhất khi người dùng đang đứng ở Trang chính (`library()`)** mới được phép hiện hộp thoại xác nhận thoát app (`confirmExit()`).

---

## 3. QUY TẮC TẢI KHI CẮM SẠC & KHÔNG GIỚI HẠN CHƯƠNG (FULL DOWNLOAD)
* **Nguyên nhân lỗi cũ:**
  1. Mã nguồn cũ áp dụng cửa sổ trượt 15 chương (`AHEAD = 15`), và hàm `prune()` tự động xóa các chương lớn hơn `current + 15`.
  2. Tự động tải ngầm toàn bộ truyện trong Tủ sách kể cả những truyện chưa từng đọc, gây hao pin và tốn bộ nhớ.
* **Quy tắc bắt buộc:**
  1. **Chỉ tải cho truyện đã và đang đọc:** Trong `Repository.run`, vòng lặp tải tự động chỉ đưa vào hàng đợi những bộ truyện mà người dùng **đã thực sự mở đọc trên máy** (`b.stamp > 0 || b.visits > 0 || b.id.equals(activeBook)`). Tuyệt đối không tự động tải các bộ chưa đọc trong Tủ sách.
  2. **Không giới hạn 15 chương:** Bắt đầu từ chương đang đọc dở (`current`), hệ thống tải liên tục toàn bộ các chương tiếp theo cho đến chương cuối cùng của truyện.
  3. **Bảo lưu vĩnh viễn:** Trong `Store.prune(Book b)`, chỉ được phép dọn dẹp các chương cũ đã đọc xong từ lâu (`ord < current.ord - 3`). Tuyệt đối **KHÔNG BAO GIỜ xóa bất kỳ chương nào từ vị trí hiện tại trở đi (`ord >= current.ord`)**.

---

## 4. QUY TẮC HIỂN THỊ TRẠNG THÁI E-INK (TRÁNH HAO PIN DO FLASH MÀN HÌNH)
* **Nguyên nhân lỗi cũ:** Dùng `Toast` thông báo tiến trình tải. Trên màn hình E-ink, mỗi thông báo Toast xuất hiện và biến mất sẽ ép toàn bộ màn hình phải refresh/chớp nháy 2 lần, gây lưu ảnh (ghosting) và tiêu tốn pin nghiêm trọng.
* **Quy tắc bắt buộc:**
  1. **Không dùng Toast thông báo tiến trình tải:** Loại bỏ toàn bộ `Toast` hiển thị tiến trình tải ngầm.
  2. **Vị trí hiển thị trạng thái tải tĩnh (Zero-Flicker):**
     * **Trang chính (`library`):** Hiển thị viên pin / nhãn capsule nhỏ gọn ở **góc dưới bên phải** (`[ ⤓ 65% ]` khi tải, `[ ✓ 100% ]` khi xong).
     * **Màn hình đọc (`NativeReader`):** Hiển thị ở **giữa góc dưới đáy màn hình** (`c.drawText(centerStatus, getWidth()/2f, footerY, statusPaint)`).
     * Người dùng nhìn xuống chân trang là biết tiến độ, khi thấy `✓ 100%` là biết tắt Wi-Fi mà không cần bất kỳ thông báo nào làm gián đoạn việc đọc.

---

## 5. Tiến độ tải trong Yêu thích
* Với truyện không chọn lưu full: chương đã đọc trước vị trí hiện tại được tính hoàn thành, giữ quy tắc cũ.
* Với truyện chọn tải full: tính số chương thực sự đọc được trên máy / tổng số chương. Không tính chương cũ đã bị xóa là đã lưu; FULL 100% chỉ khi toàn bộ còn đủ nội dung.

## 6. Bố cục sáu tựa truyện (yêu cầu 0.5.2)
* Ở cỡ chữ hệ thống mặc định: sáu thẻ nằm trọn vùng nhìn. Tên truyện 17sp, thông tin 12sp, icon lớn hơn. Không đo từ toàn bộ màn hình rồi trừ một con số ước lượng.
* Tên dài giới hạn hai dòng với dấu ba chấm; giữ thẻ để xem đầy đủ. Nếu cỡ chữ hệ thống quá lớn/vùng nhìn không đủ, giảm số thẻ để tránh cắt nét chữ.
* Mục lục dùng cùng cách đo; các hàng chương được giới hạn hai dòng và có giữ để xem tên đầy đủ.

## 7. Phím khóa cảm ứng (yêu cầu 0.5.2)
* Bỏ tổ hợp Vol+ và Vol− vì không hoạt động trên máy người dùng.
* Giữ riêng Vol+/phím Page Up/phím lùi 700ms để đổi khóa một lần; lần thả sau giữ không lật trang. Bấm ngắn lật khi thả. Hủy timer khi mất focus/Activity dừng, bỏ auto-repeat.
* Khóa chặn cảm ứng toàn Activity. Xác nhận cuối chương dùng overlay trong reader để vẫn có thể giữ phím khóa nhanh lúc chuẩn bị đút túi.

## 8. Yêu thích và xác nhận đã đọc
* Yêu thích gồm các bộ chọn theo dõi đặc biệt, bấm tải full, và có lượt đọc; ưu tiên bộ chọn riêng, tải full, rồi lượt đọc cao. Không xếp theo cập nhật. Chạm tựa mở chọn chương.
* Vừa đọc có icon tải toàn bộ từng truyện; bấm tải full lưu chế độ giữ đủ và đưa vào nhóm ưu tiên Yêu thích.
* Cuối chương mới nhất chỉ có một xác nhận cập nhật HAKO; xác nhận gọi thẳng thao tác cập nhật, không gọi một hàm mở xác nhận thứ hai. Kết quả thành công hiển thị chân trang.

## 0.5.3 — visual weight and quiescent reading

- UI titles use system sans-serif weight 500, 16sp; headings 18sp. Body reader fonts/size chosen by the user are retained. HTML bold is normalized to weight 500, preserving italics; oversized HTML headings are capped at 1.15× the base reading size. Large icon geometry remains, strokes are 1.35 vector units; card borders 1dp.
- No lock text overlay. Native reader footer remains the lock indicator. Touch and Menu/Enter are consumed while locked. Android notification shade is outside this lock; ordinary app privileges cannot disable it. No global overlay or fake kiosk lock is installed.
- On battery, locking or leaving the Activity pauses automatic downloads after the in-flight chapter. Manual full/sync requests and charging jobs remain explicit exceptions. Unlock does not start a new network request itself.
- DownloadService waits on completion notification instead of polling every 500ms. No app wake lock or forced screen-on flag. WebView browsing is paused on Activity pause; native cached reading has no refresh timer.
- Disable window transition animation and list overscroll glow. Retain necessary input scrolling only.
- Emulator idle tests sample process CPU over three seconds, count redraws, verify no download and no forced screen-on flag. These are app behavior checks, not battery measurements or proof that the whole SoC is idle.
