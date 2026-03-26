# warehouse-management-backend

Parent Maven (multi-module) cho hệ thống warehouse-management.

## Cấu trúc module

| Module | Mô tả |
|--------|--------|
| `common-lib` | DTO/API chung, exception, Excel (không chứa client gọi microservice khác) |
| `eureka-server` | Service discovery |
| `api-gateway` | Spring Cloud Gateway, route tới các service |
| `auth-service` | Đăng ký / đăng nhập / JWT |
| `product-service` | Sản phẩm, danh mục, nhà cung cấp |
| `warehouse-service` | Kho, vị trí, tồn kho |
| `inbound-service` | Đơn nhập, putaway; OpenFeign (`com.inbound_service.client` → warehouse-service) |
| `outbound-service` | Đơn xuất; OpenFeign (`com.outbound_service.client` → warehouse-service) |

## Build

Từ thư mục gốc:

```bash
./mvnw.cmd -DskipTests compile
./mvnw.cmd test
```

(Trên Linux/macOS: `./mvnw`.)

## Thêm service mới

1. Tạo thư mục module cùng cấp với các service hiện có (ví dụ `inventory-service`).
2. Thêm `<module>inventory-service</module>` vào `pom.xml` của parent.
