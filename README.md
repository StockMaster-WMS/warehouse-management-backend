# Warehouse Management Backend

Backend hệ thống quản lý kho đang chạy theo hướng **modular monolith**: một Spring Boot application, một PostgreSQL database, nhiều module nghiệp vụ tách theo package.

## Công nghệ chính

- Java 17
- Spring Boot 3.5.x
- Spring Data JPA
- Flyway
- PostgreSQL
- Spring Security
- Springdoc OpenAPI
- MapStruct
- Docker / Docker Compose

## Kiến trúc

```text
Client / Frontend
  |
  v
warehouse-management-backend :9000
  |
  +-- auth_service
  +-- product_service
  +-- warehouse_service
  +-- inbound_service
  +-- outbound_service
  +-- common
  |
  v
PostgreSQL warehouse_management
```

Các package nghiệp vụ vẫn tách rõ theo module, nhưng build/deploy chỉ còn một app. Eureka, API Gateway, Feign client nội bộ, database riêng theo service và `outbox_events` không còn là một phần của runtime chính.

## Cấu trúc dự án

```text
warehouse-management-backend/
├─ pom.xml
├─ docker-compose.yml
├─ Dockerfile
├─ .env
├─ .env.example
├─ mvnw
├─ mvnw.cmd
└─ src/main/
   ├─ java/com/
   │  ├─ WarehouseApplication.java
   │  ├─ config/
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
WAREHOUSE_APP_PORT=9000
FRONTEND_ORIGIN=http://localhost:3000,https://warehouse.codestack.live

WAREHOUSE_DB_URL=jdbc:postgresql://localhost:5432/warehouse_management
WAREHOUSE_DB_USERNAME=postgres
WAREHOUSE_DB_PASSWORD=postgres
WAREHOUSE_FLYWAY_ENABLED=true

AUTH_JWT_SECRET=replace-with-a-very-long-random-secret-at-least-32-bytes
AUTH_JWT_ISSUER=warehouse-app
AUTH_JWT_ACCESS_EXPIRATION_SECONDS=3600
AUTH_COOKIE_SECURE=false
AUTH_COOKIE_SAME_SITE=Lax
AUTH_BOOTSTRAP_DEFAULT_USERS_ENABLED=false
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
.\mvnw.cmd spring-boot:run
```

Swagger UI:

```text
http://localhost:9000/swagger-ui.html
```

## Chạy bằng Docker Compose

```powershell
docker compose up --build
```

Docker Compose khởi chạy:

- `warehouse-app` tại `http://localhost:9000`
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

Các API nghiệp vụ yêu cầu access token JWT. `SecurityConfig` cho phép các endpoint đăng nhập, refresh, introspect, health và tài liệu OpenAPI truy cập công khai; các request còn lại đi qua `JwtAuthenticationFilter` trước khi Spring Security kiểm tra quyền.
