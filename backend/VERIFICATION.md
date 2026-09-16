# Kết quả kiểm chứng — 2026-09-16

## Build và test

Đã build thành công 5 JAR Java 21. Các test cuối của mỗi module:

| Module | Số test | Kết quả |
| --- | --- | --- |
| identity-service | 5 | Passed |
| clinic-service | 4 | Passed |
| social-service | 5 | Passed |
| media-service | 3 | Passed |
| api-gateway | 62 | Passed |
| Tổng | 79 | Passed |

Lệnh build toàn bộ đã chạy thành công; sau đó build/test lại riêng Identity sau thay đổi đăng ký và cấu hình Redis. Formatter chạy cùng build.

Các test kiểm tra Flyway migration trên H2/MySQL mode, ownership entity từng service, đăng ký/đăng nhập với mật khẩu hash, introspection và thu hồi token, phân quyền kích hoạt Clinic, outbox commit/rollback/retry, feed và quyền xóa bài, Swagger, HTTP Identity, 48 request/response cũ qua Gateway và 12 trường hợp chọn upstream.

## Docker và MySQL thật

Đã build 5 image qua `compose.local-build.yaml`, chạy bằng Docker Engine với:

- Ba MySQL 8.4 độc lập: identity-db, clinic-db, social-db.
- Redis riêng của project, cổng host 6380.
- Năm ứng dụng; Gateway công khai cổng 8080.

`smoke-microservices.ps1` đã chạy thành công:

1. Gateway health trả 200; ba tài liệu OpenAPI trả thành công.
2. Request có JWT qua Gateway → Clinic → Identity thành công.
3. Tạo, đọc feed và xóa bài qua Gateway → Social → Identity với MySQL thật thành công.
4. Gateway chặn đường dẫn /internal/identity bằng 404.
5. Tài khoản và bài viết thử đã được xóa sau kiểm tra.

## PowerShell local

Đã khởi động cả 5 JAR bằng `start-local.ps1`, từng service vượt qua kiểm tra health.

Sau đó tạm đổi tên `.local/services.json` để mô phỏng mất file PID. `stop-local.ps1` tìm và dừng đúng cả 5 tiến trình theo đường dẫn JAR. Kiểm tra netstat xác nhận không còn listener trên 8080–8084.

Sau kiểm tra, các ứng dụng đã dừng; ba database phát triển và Redis của project còn chạy tại thời điểm kiểm chứng. Các script local và cấu hình build local được nhắc trong tài liệu này sau đó đã bị xóa khi chuẩn bị deploy AWS; đây là kết quả lịch sử, không phải hướng dẫn chạy hiện tại. Xem `DEPLOY_AWS.md` để triển khai.

## Giới hạn của kết quả

Không gọi các provider thật để gửi email/OTP, upload ảnh, tư vấn AI, bản đồ hoặc push notification. Không xác nhận tích hợp frontend hay năng lực chịu tải. Database cũ không được chuyển dữ liệu; môi trường mới sử dụng database trống theo yêu cầu.
