# StockMaster Backend

Backend cho StockMaster, một hệ thống quản lý kho hỗ trợ doanh nghiệp theo dõi sản phẩm, tồn kho, nhập hàng, xuất hàng, kiểm kê, báo cáo, phân quyền người dùng và trợ lý AI nội bộ. Dự án được xây dựng theo hướng modular monolith với Spring Boot, PostgreSQL, JWT authentication và Flyway migration.

Repository này tập trung vào REST API, nghiệp vụ kho và các thành phần nền tảng như bảo mật, phân quyền, migration database, tài liệu Swagger và import/export Excel.

## Vai trò của tôi

- Phân tích nghiệp vụ quản lý kho: sản phẩm, kho, vị trí lưu trữ, tồn kho, nhập hàng, xuất hàng, kiểm kê và hoàn trả.
- Thiết kế REST API, database schema, migration Flyway và phân tách module theo domain nghiệp vụ.
- Xây dựng xác thực JWT, refresh token bằng HttpOnly cookie, phân quyền theo vai trò và kiểm soát truy cập theo kho.
- Phát triển các luồng nghiệp vụ chính: purchase order, inbound receipt, putaway, sales order, picking, stock movement và cycle count.
- Tích hợp import/export Excel, Swagger UI, báo cáo, thông báo, audit log và trợ lý AI dùng Ollama local.
- Viết test cho các phần rủi ro cao như security, CORS, JWT, idempotency, stock flow và AI authorization.

## Điểm nổi bật

- Authentication/authorization với JWT access token, refresh cookie và role-based access control.
- Quản lý kho theo nhiều domain: product, warehouse, inbound, outbound, inventory, report, user management.
- Theo dõi tồn kho, cảnh báo tồn thấp/gần hết hạn và lịch sử dịch chuyển hàng hóa.
- Luồng nhập hàng đầy đủ: purchase order, phiếu nhập, gợi ý putaway và cập nhật tồn kho.
- Luồng xuất hàng đầy đủ: sales order, picking, cập nhật tồn kho và ghi nhận stock movement.
- Kiểm kê kho, hoàn trả hàng, audit log, notification và dashboard summary.
- Import/export Excel bằng Apache POI.
- Database migration bằng Flyway, dễ tái tạo môi trường.
- Swagger/OpenAPI để kiểm thử và đọc tài liệu API nhanh.
- Có Docker Compose để chạy backend cùng PostgreSQL.

## Kiến trúc tổng quan

```text
Frontend Next.js
      |
      | REST API + JWT
      v
Spring Boot Backend
      |
      | JPA / Flyway
      v
PostgreSQL

Ollama local được dùng cho các tính năng AI assistant khi cấu hình model.
```

## Tech stack

- Java 17
- Spring Boot 3.5
- Spring Web, Spring Security, Spring Data JPA, Validation, Actuator
- PostgreSQL
- Flyway
- MapStruct, Lombok
- JJWT
- Apache POI
- Springdoc OpenAPI / Swagger UI
- Docker, Docker Compose
- JUnit / Spring Boot Test

## Tài khoản demo

| Vai trò | Tài khoản | Mật khẩu |
| --- | --- | --- |
| Admin | `admin` | `AdmIn@12345` |

> Tài khoản trên dùng cho môi trường demo/local. Không dùng mật khẩu này cho môi trường production.

## Yêu cầu môi trường

- JDK 17+
- Maven Wrapper có sẵn trong dự án
- PostgreSQL 14+ nếu chạy local không dùng Docker
- Docker Desktop nếu chạy bằng Docker Compose
- Ollama nếu muốn chạy tính năng AI local

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
| `AUTH_COOKIE_SAME_SITE` | SameSite cho refresh cookie; dùng `None` khi frontend/backend khác site |
| `AUTH_BOOTSTRAP_DEFAULT_USERS_ENABLED` | Bật tạo user mặc định nếu cần seed dữ liệu |
| `AI_OLLAMA_API_URL` | URL Ollama local |
| `AI_OLLAMA_MODEL` | Tên model AI local |

Không commit `.env` chứa thông tin thật lên repository.

## Chạy bằng Docker Compose

Cách này khởi động backend và PostgreSQL:

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

## API documentation

Swagger UI:

```text
http://localhost:9000/swagger-ui.html
```

Một số nhóm endpoint chính:

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
- `/api/inbound-receipts/**` - phiếu nhập
- `/api/putaway-tasks/**` - tác vụ putaway
- `/api/sales-orders/**` - đơn xuất hàng
- `/api/picking-items/**` - tác vụ picking
- `/api/cycle-counts/**` - kiểm kê
- `/api/rma/**` - hoàn trả
- `/api/reports/**` - báo cáo
- `/api/notifications/**` - thông báo
- `/api/audit-logs/**` - nhật ký hệ thống
- `/api/v1/ai/**` - trợ lý AI và gợi ý vị trí

## Database migration

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

## Kiểm thử và build

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
├─ common              # dashboard, report, audit, notification, tiện ích dùng chung
└─ config              # cấu hình ứng dụng
```

## Ghi chú vận hành

- Backend mặc định chạy ở port `9000`.
- Frontend local thường chạy ở `http://localhost:3000`; cần có trong `FRONTEND_ORIGIN`.
- Hầu hết API nghiệp vụ yêu cầu JWT access token.
- Swagger, health check và các API auth cần thiết được mở public theo cấu hình bảo mật.
- Khi deploy HTTPS, đặt `AUTH_COOKIE_SECURE=true` và cấu hình domain CORS chính xác.
- Nếu frontend và backend khác site, đặt `AUTH_COOKIE_SAME_SITE=None`; cookie `SameSite=None` bắt buộc phải đi cùng `Secure`.
- Nếu để `AUTH_COOKIE_SECURE=auto`, proxy/CDN phải forward đúng `X-Forwarded-Proto: https` để backend tự set refresh cookie thành `Secure; SameSite=None`.

## Trạng thái dự án

- Đã hoàn thành các luồng chính: authentication, dashboard, product, warehouse, inventory, inbound, outbound, cycle count, reports, user management và AI assistant.
- Có test cho một số nghiệp vụ và lớp bảo mật quan trọng.
- Có thể cải thiện thêm CI/CD, coverage report, seed data chuẩn hóa và tài liệu API theo từng use case.
