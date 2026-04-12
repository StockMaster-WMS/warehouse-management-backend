# Warehouse Management Backend

Backend cho hệ thống quản lý kho được tổ chức theo mô hình microservice, build bằng Maven multi-module và Spring Boot.

## Công nghệ chính

- Java 17
- Spring Boot 3.5.x
- Spring Cloud 2025.x
- Spring Cloud Gateway
- Eureka Server / Eureka Client
- Spring Data JPA
- Flyway
- PostgreSQL
- OpenFeign
- Springdoc OpenAPI
- MapStruct
- Docker / Docker Compose

## Kiến trúc tổng quan

```text
Client
  |
  v
API Gateway
  |
  +--> auth-service
  +--> product-service
  +--> warehouse-service
  +--> inbound-service
  +--> outbound-service

Eureka Server: service discovery
common-lib: thư viện dùng chung giữa các service
Mỗi service nghiệp vụ có cơ sở dữ liệu riêng
```

## Cấu trúc dự án

```text
warehouse-management-backend/
├─ pom.xml
├─ docker-compose.yml
├─ Dockerfile
├─ .env
├─ .env.example
├─ load-env.ps1
├─ common-lib/
├─ eureka-server/
├─ api-gateway/
├─ auth-service/
├─ product-service/
├─ warehouse-service/
├─ inbound-service/
└─ outbound-service/
```

## Các module

| Module | Vai trò |
|--------|---------|
| `common-lib` | DTO, exception, utility và thành phần dùng chung |
| `eureka-server` | Service registry cho toàn hệ thống |
| `api-gateway` | Điểm vào tập trung, route request tới các service và gom tài liệu Swagger |
| `auth-service` | Đăng ký, đăng nhập, JWT |
| `product-service` | Quản lý sản phẩm, danh mục, nhà cung cấp |
| `warehouse-service` | Quản lý kho, vị trí lưu trữ, tồn kho |
| `inbound-service` | Nghiệp vụ nhập hàng, phiếu nhập, putaway |
| `outbound-service` | Nghiệp vụ xuất hàng, đơn bán, picking |

## Yêu cầu môi trường

- JDK 17
- Docker Desktop + Docker Compose nếu chạy bằng container
- PostgreSQL nếu chạy local không dùng Docker
- PowerShell hoặc terminal tương đương

## Cấu hình môi trường

Các service đều hỗ trợ đọc biến môi trường từ file `.env` ở thư mục gốc repo.

1. Tạo file `.env` từ `.env.example`.
2. Bổ sung các biến còn thiếu nếu cần, đặc biệt là phần `auth-service`.

Ví dụ tối thiểu:

```env
EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka

EUREKA_SERVER_PORT=8761
API_GATEWAY_PORT=9000
AUTH_SERVICE_PORT=9011
PRODUCT_SERVICE_PORT=9002
WAREHOUSE_SERVICE_PORT=9003
INBOUND_SERVICE_PORT=9004
OUTBOUND_SERVICE_PORT=9005

AUTH_DB_URL=jdbc:postgresql://localhost:5432/auth_db
AUTH_DB_USERNAME=postgres
AUTH_DB_PASSWORD=postgres

PRODUCT_DB_URL=jdbc:postgresql://localhost:5432/db_product
PRODUCT_DB_USERNAME=postgres
PRODUCT_DB_PASSWORD=postgres

WAREHOUSE_DB_URL=jdbc:postgresql://localhost:5432/db_warehouse
WAREHOUSE_DB_USERNAME=postgres
WAREHOUSE_DB_PASSWORD=postgres

INBOUND_DB_URL=jdbc:postgresql://localhost:5432/db_inbound
INBOUND_DB_USERNAME=postgres
INBOUND_DB_PASSWORD=postgres

OUTBOUND_DB_URL=jdbc:postgresql://localhost:5432/db_outbound
OUTBOUND_DB_USERNAME=postgres
OUTBOUND_DB_PASSWORD=postgres

GATEWAY_SECURITY_ENABLED=true
INTERNAL_SECURITY_ENABLED=true
INTERNAL_SERVICE_TOKEN=change-me-internal-service-token
AUTH_MODE=secure
AUTH_JWT_SECRET=change-me-to-a-strong-secret
AUTH_BOOTSTRAP_DEFAULT_USERS_ENABLED=false
GATEWAY_URL=http://localhost:9000
```

Nếu đang dùng PowerShell, có thể nạp biến môi trường từ `.env` bằng:

```powershell
.\load-env.ps1
```

## Chạy bằng Docker Compose

Chạy toàn bộ hệ thống:

```powershell
docker compose up --build
```

Docker Compose hiện tại khởi chạy:

- `eureka-server`
- `api-gateway`
- `auth-service`
- `product-service`
- `warehouse-service`
- `inbound-service`
- `outbound-service`
- 5 container PostgreSQL riêng cho từng service nghiệp vụ

Các cổng mặc định được publish ra máy host:

| Thành phần | Cổng |
|-----------|------|
| Eureka Server | `8761` |
| API Gateway | `9000` |
| PostgreSQL Auth | `5433` |
| PostgreSQL Product | `5434` |
| PostgreSQL Warehouse | `5435` |
| PostgreSQL Inbound | `5436` |
| PostgreSQL Outbound | `5437` |

Trong Docker Compose, các service `auth-service`, `product-service`, `warehouse-service`,
`inbound-service` và `outbound-service` chỉ `expose` port trong Docker network. Client nên gọi
qua `http://localhost:9000` để đi qua lớp xác thực của API Gateway.

`INTERNAL_SERVICE_TOKEN` là token nội bộ giữa gateway và các service. Khi deploy thật, cần đổi
giá trị này cùng với `AUTH_JWT_SECRET`; không dùng các giá trị `change-me-*`.

## Chạy local bằng Maven

Trước khi chạy local:

1. Tạo các database: `auth_db`, `db_product`, `db_warehouse`, `db_inbound`, `db_outbound`.
2. Cập nhật file `.env`.
3. Nạp biến môi trường bằng `.\load-env.ps1` nếu cần.

Build toàn bộ dự án:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Chạy từng service ở các terminal riêng theo thứ tự:

```powershell
.\mvnw.cmd -pl eureka-server -am spring-boot:run
.\mvnw.cmd -pl api-gateway -am spring-boot:run
.\mvnw.cmd -pl auth-service -am spring-boot:run
.\mvnw.cmd -pl product-service -am spring-boot:run
.\mvnw.cmd -pl warehouse-service -am spring-boot:run
.\mvnw.cmd -pl inbound-service -am spring-boot:run
.\mvnw.cmd -pl outbound-service -am spring-boot:run
```

Chạy test:

```powershell
.\mvnw.cmd test
```

## Endpoint chính

| Thành phần | URL |
|-----------|-----|
| Eureka Dashboard | `http://localhost:8761` |
| Gateway Swagger UI | `http://localhost:9000/swagger-ui.html` |

Các route chính đi qua gateway:

- `/api/auth/**`
- `/api/products/**`
- `/api/categories/**`
- `/api/suppliers/**`
- `/api/warehouse/**`
- `/api/warehouses/**`
- `/api/locations/**`
- `/api/stocks/**`
- `/api/inbound/**`
- `/api/inbounds/**`
- `/api/purchase-orders/**`
- `/api/po-items/**`
- `/api/putaway-tasks/**`
- `/api/inbound-receipts/**`
- `/api/outbound/**`
- `/api/outbounds/**`
- `/api/sales-orders/**`
- `/api/picking-items/**`
- `/api/so-items/**`

## Build image cho từng service

`Dockerfile` ở thư mục gốc build image theo `SERVICE` argument. Ví dụ:

```powershell
docker build --build-arg SERVICE=api-gateway -t warehouse/api-gateway .
```

## Ghi chú

- `pom.xml` ở thư mục gốc là parent POM, quản lý version chung và danh sách module.
- `common-lib` không phải ứng dụng chạy độc lập.
- `api-gateway` đang là điểm truy cập ưu tiên cho client và Swagger.
