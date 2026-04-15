# Warehouse Management Backend

Backend hệ thống quản lý kho hiện được tổ chức theo hướng modular monolith: một Spring Boot application, một database PostgreSQL, nhiều module nghiệp vụ tách theo package.

## Công nghệ chính

- Java 17
- Spring Boot 3.5.x
- Spring Data JPA
- Flyway
- PostgreSQL
- OpenFeign
- Springdoc OpenAPI
- MapStruct
- Docker / Docker Compose

## Kiến trúc

```text
Client / Frontend
  |
  v
warehouse-app :8080
  |
  +-- auth module
  +-- product module
  +-- warehouse module
  +-- inbound module
  +-- outbound module
  +-- common module
  |
  v
PostgreSQL warehouse_management
```

Các package nghiệp vụ vẫn tách rõ theo module, nhưng build/deploy chỉ còn một app. Các bảng `outbox_events`, Eureka Server và API Gateway đã được bỏ khỏi backend chính.

## Cấu trúc dự án

```text
warehouse-management-backend/
├─ pom.xml
├─ docker-compose.yml
├─ Dockerfile
├─ .env
├─ .env.example
├─ load-env.ps1
└─ warehouse-app/
   ├─ pom.xml
   └─ src/main/
      ├─ java/com/
      │  ├─ warehouse_app/
      │  ├─ auth_service/
      │  ├─ product_service/
      │  ├─ warehouse_service/
      │  ├─ inbound_service/
      │  ├─ outbound_service/
      │  └─ common/
      └─ resources/
         ├─ application.yaml
         └─ db/migration/
```

## Cấu hình môi trường

Tạo `.env` từ `.env.example` nếu cần:

```env
WAREHOUSE_APP_PORT=8080
FRONTEND_ORIGIN=http://localhost:3000

WAREHOUSE_DB_URL=jdbc:postgresql://localhost:5432/warehouse_management
WAREHOUSE_DB_USERNAME=postgres
WAREHOUSE_DB_PASSWORD=postgres
WAREHOUSE_FLYWAY_ENABLED=true

AUTH_MODE=public
AUTH_JWT_SECRET=replace-with-a-very-long-random-secret-at-least-32-bytes
AUTH_JWT_ISSUER=warehouse-app
AUTH_JWT_ACCESS_EXPIRATION_SECONDS=3600
AUTH_COOKIE_SECURE=false
AUTH_COOKIE_SAME_SITE=Lax
AUTH_BOOTSTRAP_DEFAULT_USERS_ENABLED=false

WAREHOUSE_INTERNAL_BASE_URL=http://localhost:8080
```

Nạp biến môi trường bằng PowerShell:

```powershell
.\load-env.ps1
```

## Chạy local bằng Maven

Tạo database:

```sql
CREATE DATABASE warehouse_management;
```

Build:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Chạy app:

```powershell
.\mvnw.cmd -pl warehouse-app spring-boot:run
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## Chạy bằng Docker Compose

```powershell
docker compose up --build
```

Docker Compose khởi chạy:

- `warehouse-app` tại `http://localhost:8080`
- `postgres` tại `localhost:5432`, database `warehouse_management`

## Endpoint chính

- `/api/auth/**`
- `/api/products/**`
- `/api/categories/**`
- `/api/suppliers/**`
- `/api/warehouses/**`
- `/api/locations/**`
- `/api/stocks/**`
- `/api/purchase-orders/**`
- `/api/po-items/**`
- `/api/putaway-tasks/**`
- `/api/inbound-receipts/**`
- `/api/sales-orders/**`
- `/api/picking-items/**`
- `/api/so-items/**`
- `/api/customers/**`

## Ghi chú bảo mật

File `.env` chỉ dùng local và không được đưa credentials thật vào source bundle. Nếu credentials thật đã từng bị chia sẻ, cần rotate trên nhà cung cấp database.

`AUTH_MODE=public` giữ hành vi tương thích với các service cũ trong môi trường dev. Khi làm cứng bảo mật, cần thêm JWT verification filter/resource server cho toàn bộ API rồi chuyển sang `AUTH_MODE=secure`.
