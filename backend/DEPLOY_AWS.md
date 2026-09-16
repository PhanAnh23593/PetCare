# Deploy ComeMyWay lên AWS EC2

Hướng dẫn này chạy 5 service Java, 3 MySQL, Redis và Caddy trên **một EC2**. Phù hợp bản demo/triển khai ban đầu; khi EC2 dừng thì toàn bộ hệ thống dừng. Dữ liệu nằm trong Docker volumes trên EBS, không phải RDS. Chưa có cơ chế high availability hay backup tự động.

## 1. Push backend lên GitHub từ máy Windows

Mở PowerShell tại `backend`:

```powershell
git status --short
git add -- .
git diff --cached --stat
git diff --cached --name-only
git diff --cached --check
git commit -m "Prepare microservices deployment on AWS EC2"
git push
```

Kiểm tra danh sách trước commit: không có `.env`, `.env.aws`, private key, service account, log, `target/`, `.local/`, `.m2repo/`, `.idea/`. Chỉ commit hai file mẫu `.env.example` và `.env.aws.example`. Nếu có file bí mật đã được track từ trước, `.gitignore` không tự bỏ chúng khỏi Git; cần bỏ track và thay khóa nếu đã từng push.

Git root hiện là thư mục cha `HIT-ComeMyWay`, nên dùng `git add -- .` ngay trong `backend`, tránh `git add -A` ở root kéo cả cấu hình IDE của thư mục cha vào commit. Kiểm tra cả file đã stage từ trước bằng `git diff --cached --name-only`.

Những dòng xóa `backend/src/...` là thay đổi tách microservices có sẵn: commit cùng các module mới. Giữ `pet-platform-service/` và `compatibility/`: chúng lưu mã đối chiếu, fixture và bằng chứng migration; test Gateway hiện đọc fixture từ module cũ. File `.env` thật, bộ script chạy local và cấu hình đóng image từ JAR local đã được xóa. Cache và log local đã được ignore.

## 2. Tạo EC2

- Chọn region gần người dùng, ví dụ Singapore.
- AMI: **Ubuntu Server 24.04 LTS, x86_64**.
- Gợi ý ban đầu: **t3.large (2 vCPU, 8 GiB RAM)**, EBS gp3 **40 GiB**. Đây là ước lượng cho nhiều JVM + MySQL, cần theo dõi RAM/CPU/disk khi chạy thật; build source có thể cần thêm tài nguyên. Không mặc định cấu hình này miễn phí.
- Tạo key pair, giữ file `.pem` ngoài repo. Bật mã hóa EBS; cân nhắc bỏ `Delete on termination` cho volume dữ liệu cần giữ.
- Đặt instance trong public subnet có Internet Gateway và public IPv4; gắn Elastic IP để DNS không đổi sau stop/start.
- Security Group inbound: TCP **22 chỉ từ IP của bạn**, TCP **80 và 443 từ Internet**. Không mở 3306–3309, 6379–6380 hay 8080–8084. Giữ outbound để tải image/dependency và gọi các provider.
- Tạo DNS A `api.tenmiencuaban.com` trỏ tới Elastic IP. Chỉ thêm AAAA nếu đã cấu hình IPv6 thực sự.

Tham khảo [cấu hình EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-launch-parameters.html) và [Security Group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/creating-security-group.html). EC2, EBS và public IPv4 có thể phát sinh phí; kiểm tra AWS console trước khi tạo.

## 3. SSH và cài Docker

Từ PowerShell, thay đường dẫn key và IP:

```powershell
ssh -i C:\Keys\comemyway.pem ubuntu@EC2_PUBLIC_IP
```

Các lệnh dưới đây chạy bằng **Bash trên EC2 Ubuntu mới**:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git openssl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker compose version
```

Dùng Compose **>= 2.24.4** vì file AWS dùng `!reset`/`!override` để loại cổng local. Các bước cài đặt dựa trên [Docker Ubuntu documentation](https://docs.docker.com/engine/install/ubuntu/).

## 4. Clone và cấu hình riêng trên server

```bash
git clone https://github.com/YOUR_ACCOUNT/HIT-ComeMyWay.git
cd HIT-ComeMyWay/backend
umask 077
cp .env.aws.example .env.aws
mkdir -p secrets backups
chmod 600 .env.aws
```

Repo private: dùng SSH deploy key chỉ đọc hoặc xác thực GitHub qua cơ chế credential; không nhúng token vào URL trong lệnh.

Sinh độc lập các khóa và mật khẩu đang để trống trong file mẫu:

```bash
for key in IDENTITY_DB_PASSWORD IDENTITY_DB_ROOT_PASSWORD CLINIC_DB_PASSWORD CLINIC_DB_ROOT_PASSWORD SOCIAL_DB_PASSWORD SOCIAL_DB_ROOT_PASSWORD JWT_SECRET CLINIC_INTERNAL_TOKEN SOCIAL_INTERNAL_TOKEN MEDIA_INTERNAL_TOKEN; do
  if grep -qx "${key}=" .env.aws; then
    sed -i "s/^${key}=$/${key}=$(openssl rand -hex 32)/" .env.aws
  fi
done
nano .env.aws
```

Điền `API_DOMAIN` bằng tên miền thật, không có `https://` hay đường dẫn. Điền Brevo + email sender đã xác minh để đăng ký/OTP hoạt động; Cloudinary để upload; Gemini để AI hoạt động. Không sao chép mật khẩu database local sang AWS. Khi nhập giá trị có `$` hoặc `#`, dùng dấu nháy đơn theo cú pháp `.env` của Compose.

Nếu cần admin đầu tiên, điền `BOOTSTRAP_ADMIN_EMAIL`, username và password riêng ít nhất 12 ký tự. Sau khi đăng nhập admin thành công, xóa giá trị password và recreate Identity như bước 7.

Nếu dùng Firebase push, chuyển service account tới `backend/secrets/firebase-service-account.json` trên EC2. Thư mục này chỉ mount read-only vào Clinic và bị loại khỏi Git/Docker build. Nếu không có file, push notification chưa hoạt động.

## 5. Build và chạy

Tạo hàm để mọi lệnh dùng đúng cấu hình AWS (khai báo lại sau mỗi lần SSH):

```bash
dc() { sudo docker compose --env-file .env.aws -f compose.yaml -f compose.aws.yaml "$@"; }
dc config --quiet
dc --parallel 1 build
dc up -d
dc ps
dc logs --tail=100 identity-service clinic-service social-service api-gateway caddy
```

Build chạy test Maven, không bỏ qua test. Build tuần tự giúp giảm tải RAM. Lần đầu cần tải dependency và image; đợi MySQL khỏe và Flyway tạo schema. `config --quiet` chỉ kiểm tra cấu hình; tránh đưa output `config` đầy đủ lên mạng vì có mật khẩu.

Caddy tự lấy chứng chỉ HTTPS khi DNS và cổng 80/443 đúng; xem [Automatic HTTPS](https://caddyserver.com/docs/automatic-https). Gateway chỉ bind localhost trên host; database/Redis không publish cổng. Redis có AOF và volume. Log container được giới hạn dung lượng.

## 6. Kiểm tra và nối frontend

Trên EC2:

```bash
curl --fail http://127.0.0.1:8080/api/v1/public/health
curl --fail https://api.tenmiencuaban.com/api/v1/public/health
```

Mở Swagger: `https://api.tenmiencuaban.com/swagger-ui/index.html`. Chọn Identity, Clinic, Social để kiểm tra từng domain; health chung không chứng minh mọi provider hoạt động. Kiểm tra thêm đăng ký/OTP, login, upload ảnh, tạo phòng khám và đặt lịch bằng tài khoản thật.

Đổi base URL frontend/mobile thành `https://api.tenmiencuaban.com` (hoặc thêm `/api/v1` nếu client hiện đang ghép URL như vậy). Nếu frontend web dùng domain khác, kiểm tra CORS của ứng dụng trước khi public.

Database AWS mới **trống**; hướng dẫn không tự chuyển dữ liệu monolith/local. Không import schema monolith thẳng vào ba DB mới: cần quy trình chuyển đổi riêng nếu muốn giữ dữ liệu cũ.

## 7. Cập nhật, backup và xử lý lỗi

Trước cập nhật, tạo backup từng DB; file backup có dữ liệu riêng tư nên giữ ngoài Git:

```bash
stamp=$(date -u +%Y%m%dT%H%M%SZ)
for domain in identity clinic social; do
  dc exec -T "${domain}-db" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"' > "backups/${domain}-${stamp}.sql" || { echo "BACKUP FAILED: ${domain}; không tiếp tục cập nhật" >&2; break; }
done
```

Chỉ cập nhật nếu không có thông báo lỗi và đã kiểm tra đủ ba file backup; sao lưu thêm ra nơi khác (ví dụ S3 với mã hóa và quyền riêng). Ba bản dump không tạo snapshot đồng thời xuyên service; nếu cần dữ liệu nhất quán cho khôi phục, dừng ghi từ ứng dụng trong lúc backup. Định kỳ thử restore trên môi trường riêng. Docker volume không thay thế backup.

```bash
git pull --ff-only
dc --parallel 1 build
dc up -d
dc ps
```

Sau khi bỏ bootstrap password:

```bash
dc up -d --force-recreate identity-service
```

Không chạy `down -v` hoặc `docker volume prune` trên server đang giữ dữ liệu. Đổi password trong `.env.aws` không đổi user MySQL đã tạo trong volume: phải đổi trong MySQL trước rồi cập nhật cấu hình app. Không reset volume để xử lý lỗi đăng nhập DB.

- `502`: xem log Gateway/domain service và `dc ps`; service có thể đang khởi động hoặc không kết nối DB.
- Lỗi chứng chỉ: kiểm tra DNS A/AAAA, Elastic IP, inbound 80/443, `dc logs caddy`.
- Container exit `137`: kiểm tra RAM bằng `free -h`, `sudo docker stats --no-stream`; tăng cấu hình instance nếu thiếu.
- OTP/upload/AI lỗi: kiểm tra khóa và tài khoản provider tương ứng.
- EC2 restart: Docker và các container có restart policy tự chạy lại; sau đó vẫn cần kiểm tra health.

Nếu cần phục vụ người dùng thật với yêu cầu uptime/backup cao, bước tiếp theo là chuyển MySQL sang RDS, cấu hình backup và cân nhắc ECS/ALB. Cấu hình một EC2 ở đây chưa đáp ứng các yêu cầu đó.
