# Build và phát hành1.0

JDK17, Gradle9.3.1, AGP9.1.0, SDK36, build-tools35.0.0, Node.js và Python3. Android min26/target34. Engine cố định tại `gecko-version.txt`; phiên bản duy nhất tại `version.properties`. Không có Gradle Wrapper đầy đủ, cài Gradle riêng.

```sh
sdkmanager 'platforms;android-35' 'platforms;android-36' 'build-tools;35.0.0'
bash test.sh
node tests/report-relay-test.cjs
node tests/read-button-test.cjs
gradle assembleRelease -Pqa=false -Pabi=armeabi-v7a --no-daemon
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release.apk
```

Giữ chứng chỉ ký để cài đè. Không đăng khóa/mật khẩu vào issue. APK ARM32 phục vụ S4/K60 hiện tại, không khẳng định mọi thiết bị chỉ ARM64 đều hỗ trợ.

## GitHub Actions

`build.yml`: JVM/JS → Android workflow → fixture API30/API34, font1.0/1.3 → QA ARM32 + release ARM32 → package/ABI/chữ ký/hash. Artifact Pocket-ARM32 và validation kèm số lượt chạy.

## Thiết bị thật

Dùng thư mục có `Hako-Pocket-Lab.apk` và `Hako-Pocket-Lab-tests.apk`:

```sh
python scripts/run-physical-fixtures.py /path/to/apks /path/to/evidence --serial SERIAL --eink
```

Chỉ thêm `--eink` cho S4. Cần adb/aapt trong PATH hoặc truyền `--adb`, `--aapt`. Runner chỉ chấp nhận QA package, đưa launcher ra trước, chụp cửa sổ đúng và kiểm app chính không đổi. MIUI từ chối USB thì cho phép trên máy, chỉ cài lại gói thất bại. Fixture không thay thử mạng/nguồn thật.

## Bàn giao

1. Gom sửa mã, tăng code/version, kiểm toàn bộ gate.
2. Lưu commit/hash/CI/kết quả/ảnh/lỗi mở; cài đè và kiểm dữ liệu trước/sau.
3. GitHub Release có APK/SHA256/tài liệu. Upload vào file tạm, thay nguyên tử `data/releases/pocket.apk`, ghi `pocket-version.txt`.
4. Kiểm hash tải HTTPS, trang tải và QR. Giữ backup và một APK trước. Android có thể chặn hạ versionCode; downgrade không là khôi phục mặc định.
