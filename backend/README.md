# ComeMyWay — backend microservices

Backend gồm 5 ứng dụng Java 21 / Spring Boot 4.1.0. Hướng dẫn push GitHub, cấu hình môi trường và deploy EC2: [DEPLOY_AWS.md](DEPLOY_AWS.md).

| Ứng dụng | Cổng container | Trách nhiệm | Database |
| --- | --- | --- | --- |
| api-gateway | 8080 | Định tuyến API và Swagger | Không có |
| identity-service | 8081 | Auth, người dùng, OTP, token | identity_db |
| media-service | 8082 | Upload Cloudinary qua API nội bộ | Không có |
| clinic-service | 8083 | Phòng khám, lịch hẹn, nhắc hẹn, AI | clinic_db |
| social-service | 8084 | Bạn bè và Pet Locket | social_db |

Mỗi service có JAR, cấu hình và bộ test riêng. Clinic/Social lưu ID người dùng và gọi HTTP đến Identity để đọc hồ sơ, xác thực. Không có foreign key xuyên database. Flyway quản lý schema; Hibernate kiểm tra bằng `validate`.

## Triển khai AWS

Dùng `compose.yaml` cùng `compose.aws.yaml`. Tạo `.env.aws` từ `.env.aws.example` **trên server**, điền secret trước khi khởi động:

```bash
docker compose --env-file .env.aws -f compose.yaml -f compose.aws.yaml config --quiet
docker compose --env-file .env.aws -f compose.yaml -f compose.aws.yaml --parallel 1 build
docker compose --env-file .env.aws -f compose.yaml -f compose.aws.yaml up -d
```

File `.env` thật và bộ script chạy Windows local đã được loại bỏ. Các file `.env.example` và `.env.aws.example` chỉ là mẫu, không chứa khóa thật. Có thể truyền biến môi trường từ môi trường triển khai; không cần commit file secret.

Compose hiện chạy ba MySQL riêng trong container. Nếu chuyển sang database ngoài/RDS, cần cập nhật URL, username, password **và cấu hình Compose** để service không còn phụ thuộc MySQL container; chỉ thêm URL vào `.env.aws` chưa thay đổi URL đang khai báo trong Compose. Các biến ứng dụng là `IDENTITY_DB_URL/USERNAME/PASSWORD`, `CLINIC_DB_URL/USERNAME/PASSWORD`, `SOCIAL_DB_URL/USERNAME/PASSWORD`.

Database mới không tự có dữ liệu local/monolith. Nếu cần dữ liệu cũ, phải chuẩn bị migration riêng.

## Biến môi trường và API

- `JWT_SECRET`: khóa ký token; ba internal token phải được sinh riêng và giữ ở backend.
- Brevo và sender email đã xác minh: dùng cho OTP/email.
- Cloudinary: dùng cho upload; Gemini: dùng cho AI.
- Firebase tùy chọn: đặt service account trong `secrets/firebase-service-account.json` trên server; Compose AWS mount read-only vào Clinic.
- `BOOTSTRAP_ADMIN_USERNAME/EMAIL/PASSWORD`: tạo admin đầu tiên; bỏ password và recreate Identity sau khi tạo thành công.

API qua `https://<API_DOMAIN>/api/v1/...`; health ở `/api/v1/public/health`; Swagger ở `/swagger-ui/index.html`.

## Build và kiểm thử

```powershell
.\mvnw.cmd -B -ntp clean package
```

Trên Linux: `sh ./mvnw -B -ntp clean package`. Build chạy formatter và test. Test dùng H2 ở chế độ MySQL và provider giả lập, không thay thế kiểm thử MySQL/provider thật.

`pet-platform-service/` là bản Phase 1, không nằm trong Maven reactor và không được deploy. Giữ thư mục này vì test Gateway đọc fixture 48 API cũ ở đó.

Ranh giới service: [ARCHITECTURE.md](ARCHITECTURE.md). Kết quả kiểm chứng lịch sử: [VERIFICATION.md](VERIFICATION.md).
