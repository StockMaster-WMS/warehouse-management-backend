# Warehouse Management Backend

Backend cho hệ thống quản lý kho StockMaster. Dự án cung cấp REST API cho xác thực, quản lý sản phẩm, kho, tồn kho, nhập hàng, xuất hàng, kiểm kê, báo cáo và trợ lý AI.

## Công nghệ sử dụng

- Java 17
- Spring Boot 3.5
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL
- Flyway
- MapStruct, Lombok
- Springdoc OpenAPI / Swagger UI
- Docker, Docker Compose

## Yêu cầu môi trường

- JDK 17+
- Maven Wrapper có sẵn trong dự án
- PostgreSQL 14+ nếu chạy local không dùng Docker
- Docker Desktop nếu chạy bằng Docker Compose

## Cấu hình môi trường

Tạo file `.env` từ `.env.example`:

```powershell
Copy-Item .env.example .env
```

Các biến quan trọng:

| Biến | Mô tả |
| --- | --- |
| `WAREHOUSE_APP_PORT` | Port backend, mặc định `9000` |
| `FRONTEND_ORIGIN` | Danh sách origin frontend được phép gọi API |
| `WAREHOUSE_DB_URL` | JDBC URL kết nối PostgreSQL |
| `WAREHOUSE_DB_USERNAME` | Tài khoản database |
| `WAREHOUSE_DB_PASSWORD` | Mật khẩu database |
| `WAREHOUSE_FLYWAY_ENABLED` | Bật/tắt Flyway migration |
| `AUTH_JWT_SECRET` | Khóa ký JWT, cần đủ dài khi chạy thật |
| `AUTH_JWT_ISSUER` | Issuer của JWT |
| `AUTH_JWT_ACCESS_EXPIRATION_SECONDS` | Thời gian sống access token |
| `AUTH_COOKIE_SECURE` | Bật secure cookie khi dùng HTTPS |
| `AUTH_BOOTSTRAP_DEFAULT_USERS_ENABLED` | Bật tạo user mặc định nếu cần seed dữ liệu |
| `AI_OLLAMA_API_URL` | URL Ollama local |
| `AI_OLLAMA_MODEL` | Tên model AI local |

Không commit `.env` chứa thông tin thật lên repository.

## Chạy bằng Docker Compose

Cách này khởi động cả backend và PostgreSQL:

```powershell
docker compose up --build
```

Sau khi chạy:

- Backend: `http://localhost:9000`
- Swagger UI: `http://localhost:9000/swagger-ui.html`
- PostgreSQL: `localhost:5432`
- Database mặc định: `warehouse_management`

## Chạy local bằng Maven

Tạo database trước:

```sql
CREATE DATABASE warehouse_management;
```

Cập nhật `.env` cho đúng thông tin PostgreSQL local, sau đó chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Build file JAR:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Chạy file JAR sau khi build:

```powershell
java -jar target\warehouse-management-backend-0.0.1-SNAPSHOT.jar
```

## Migration database

Flyway tự chạy migration khi ứng dụng khởi động nếu `WAREHOUSE_FLYWAY_ENABLED=true`.

Các file migration nằm tại:

```text
src/main/resources/db/migration
```

Quy ước đặt tên:

```text
V001__init_schema.sql
V002__add_example_table.sql
```

Không chỉnh sửa migration đã chạy trên môi trường dùng chung. Nếu cần thay đổi schema, tạo migration mới.

## API chính

Một số nhóm endpoint đang được sử dụng:

- `/api/auth/**` - đăng nhập, đăng ký, refresh token, hồ sơ cá nhân
- `/api/users/**` - người dùng, vai trò, phân quyền
- `/api/dashboard/**` - dữ liệu tổng quan
- `/api/products/**` - sản phẩm
- `/api/categories/**` - danh mục sản phẩm
- `/api/suppliers/**` - nhà cung cấp
- `/api/customers/**` - khách hàng
- `/api/warehouses/**` - kho
- `/api/locations/**` - vị trí lưu trữ
- `/api/stocks/**` - tồn kho, cảnh báo, xuất Excel
- `/api/stocks/movements/**` - lịch sử dịch chuyển tồn kho
- `/api/purchase-orders/**` - đơn nhập hàng
- `/api/po-items/**` - dòng hàng trong đơn nhập
- `/api/inbound-receipts/**` - phiếu nhập
- `/api/putaway-tasks/**` - tác vụ putaway
- `/api/sales-orders/**` - đơn xuất hàng
- `/api/so-items/**` - dòng hàng trong đơn xuất
- `/api/picking-items/**` - tác vụ picking
- `/api/cycle-counts/**` - kiểm kê
- `/api/rma/**` - hoàn trả
- `/api/reports/**` - báo cáo
- `/api/notifications/**` - thông báo
- `/api/audit-logs/**` - nhật ký hệ thống
- `/api/v1/ai/**` - trợ lý AI và gợi ý vị trí

Chi tiết request/response xem tại Swagger UI:

```text
http://localhost:9000/swagger-ui.html
```

## Kiểm thử và kiểm tra build

Chạy test:

```powershell
.\mvnw.cmd test
```

Build nhanh không chạy test:

```powershell
.\mvnw.cmd clean package -DskipTests
```

## Cấu trúc thư mục chính

```text
src/main/java/com
├─ auth_service        # xác thực, người dùng, phân quyền
├─ product_service     # sản phẩm, danh mục, nhà cung cấp
├─ warehouse_service   # kho, vị trí, tồn kho, kiểm kê
├─ inbound_service     # PO, nhập hàng, putaway, RMA
├─ outbound_service    # SO, picking, khách hàng
├─ ai_service          # cấu hình và hội thoại AI
├─ ai_putway           # gợi ý vị trí putaway
├─ common              # dùng chung: dashboard, report, audit, notification
└─ config              # cấu hình ứng dụng
```

## Ghi chú vận hành

- Backend mặc định chạy ở port `9000`.
- Frontend local thường chạy ở `http://localhost:3000`; cần có trong `FRONTEND_ORIGIN`.
- Hầu hết API nghiệp vụ yêu cầu JWT access token.
- Swagger, health check và các API auth cần thiết được mở public theo cấu hình bảo mật.
- Khi deploy HTTPS, đặt `AUTH_COOKIE_SECURE=true` và cấu hình domain CORS chính xác.
