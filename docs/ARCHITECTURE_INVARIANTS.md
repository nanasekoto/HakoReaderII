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

## 5. CÔNG THỨC TÍNH % TIẾN ĐỘ TRONG "THƯỜNG XUYÊN ĐỌC"
* **Nguyên nhân lỗi cũ:** Lấy tổng số chương đã tải chia cho tổng số chương cả bộ truyện. Với truyện dài đã đọc 40/50 chương nhưng chỉ tải 10 chương mới, app hiển thị `20%` khiến người dùng nhầm tưởng là chưa tải đủ.
* **Quy tắc bắt buộc:**
  * **Công thức chuẩn:** `(Số chương cũ đã đọc + Số chương mới đã tải về) / Tổng số chương = 100%`.
  * Các chương trước vị trí đang đọc (`ord < currentOrd`) được tính là đã hoàn thành.
  * Khi toàn bộ các chương mới tiếp theo được tải xong, tiến độ **bắt buộc phải hiển thị là `100%`** kèm nhãn `[✓ 100%]`.

---

## 6. QUY TẮC GIAO DIỆN TỦ SÁCH 5 TRUYỆN (KHÔNG MẤT THÔNG TIN TRUYỆN CUỐI)
* **Nguyên nhân lỗi cũ:**
  1. Hàng tiêu đề và nút chức năng chiếm tới 126dp.
  2. Các thẻ truyện dùng hàm `text(...)` làm phình padding thừa lên tới 44dp bên trong mỗi thẻ.
  3. Chiều cao bị đội lên khiến truyện thứ 5 bị tràn ra ngoài màn hình và bị cắt mất chữ.
* **Quy tắc bắt buộc:**
  1. **Thanh tiêu đề siêu mỏng:** Tiêu đề và nút bấm nằm trên 1 dòng duy nhất cao tối đa `38dp`.
  2. **Padding tối ưu:** Thẻ truyện sử dụng `TextView` trực tiếp với padding sát mép (0dp trên/dưới), đệm khung `dp(5)`.
  3. **Hiển thị đủ 5 truyện:** Toàn bộ 5 thẻ truyện nằm trọn vẹn 100% bên trong màn hình mà không cần cuộn, truyện thứ 5 hiển thị đầy đủ tên truyện, số chương, ngày giờ mà không bị che khuất.



## 7. Khóa cảm ứng và phân trang sau kiểm thử APK 0.5.0
* Chỉ tổ hợp hai phím âm lượng đang được giữ đồng thời mới đổi trạng thái khóa, một lần cho tới khi cả hai được thả. Phím riêng lật trang khi thả. Không suy đoán tổ hợp từ thời gian của lần bấm trước; không lật rồi hoàn tác.
* Chiều cao thẻ lấy từ vùng ListView được Android cấp sau khi trừ tiêu đề/trạng thái, không từ chiều cao toàn màn hình. Chia dư pixel cho từng thẻ để thẻ cuối vừa khít. Phím chuyển danh sách dùng cùng số thẻ đã đo.
* Mục tiêu 5 thẻ ở cỡ chữ mặc định. Khi người dùng tăng cỡ chữ hệ thống hoặc vùng hiển thị nhỏ không đủ, giảm số thẻ để giữ chữ nguyên vẹn.
