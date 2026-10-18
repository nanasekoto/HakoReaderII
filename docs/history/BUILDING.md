# Build và kiểm tra

## Môi trường

| Thành phần | Cấu hình |
| --- | --- |
| JDK | 17 |
| Gradle | 9.3.1, cài riêng và có trong PATH |
| Android Gradle Plugin | 9.1.0 |
| Android SDK | compileSdk 36; minSdk 26; targetSdk 34 |
| Build tools | 35.0.0 cho công cụ ký/test |
| ABI phát hành S4 | armeabi-v7a |
| Engine | Phiên bản cố định trong gecko-version.txt |
| Phiên bản app | version.properties |

Repository không có Gradle Wrapper; các script wrapper thiếu JAR cũ đã được bỏ. Cài Gradle đúng phiên bản trước khi chạy. Đặt ANDROID_HOME/ANDROID_SDK_ROOT hoặc local.properties trỏ vào SDK. Cần Node.js để kiểm tra cú pháp JavaScript.

## Build local

```sh
sdkmanager 'platforms;android-36' 'build-tools;35.0.0'
bash test.sh
bash build.sh
```

build.sh chạy toàn bộ test JVM/JS rồi build release. APK có tên lấy từ version.properties trong build/deliver. Build trực tiếp cũng lấy cùng phiên bản:

```sh
gradle assembleRelease -Pabi=armeabi-v7a --no-daemon
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release.apk
```

Root build.gradle nạp ci/version.gradle. Không cần truyền -I; không sử dụng versionName/versionCode dự phòng trong app/build.gradle làm phiên bản phát hành.

test-signing.p12 là khóa hiện có của bản cá nhân, được giữ nguyên để cài đè. Không thay khóa hoặc package vn.nanase.hako khi phát hành cho người dùng hiện tại. Không yêu cầu gỡ app nếu Android từ chối chữ ký. Khi phát hành công khai cần thiết kế việc giữ khóa riêng và chuyển đổi riêng; bản dọn này không đổi danh tính ký.

## GitHub Actions

- build.yml: push main, nhánh chuẩn bị hợp nhất hoặc pull request vào main; test, build ARM32, kiểm tra ELF32/ARM và chữ ký, xuất APK + SHA256 + thông tin phiên bản.
- emulator.yml: chạy thủ công khi thay đổi UI, IPC hoặc cần kiểm tra Android. Build x86_64 riêng; không xuất APK đó cho S4. Không tự nâng Gecko.

Build thông thường không khởi động emulator để tránh thử lặp tốn thời gian. Test liên quan vẫn bắt buộc.

## Emulator riêng

Cần Android emulator, KVM và system-images;android-30;default;x86_64:

```sh
gradle assembleDebug -Pabi=x86_64 --no-daemon
mkdir -p build
jar cf build/classes.jar -C app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes .
bash test-android.sh
bash scripts/emulator-check.sh
```

scripts/emulator-check.sh xóa dữ liệu **ứng dụng trong emulator thử nghiệm** để tạo fixture. Script khóa mục tiêu adb ở emulator-5554, không gửi lệnh xóa dữ liệu tới S4. Không đổi mục tiêu này sang thiết bị có dữ liệu đọc thật. Nó dùng Android 11, 480×800, DPI 219/220; kết quả ở build/evidence. Các assert lịch sử cần rà lại khi sửa giao diện; một workflow manual chưa chạy không là bằng chứng đã vượt test.

## Đóng gói dữ liệu phụ

Giữ font, giấy phép đi kèm, bridge Gecko, extract.js, resources, Manifest và ProGuard dù release hiện chưa minify. libs/jsoup.jar phục vụ test JVM; ứng dụng dùng dependency jsoup Maven. Giấy phép jsoup trong libs và assets có mục đích khác nhau, không phải file thừa.
