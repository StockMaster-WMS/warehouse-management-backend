# warehouse-management-backend

Parent Maven (multi-module) cho hệ thống warehouse-management.

## Cấu trúc module

| Module | Mô tả |
|--------|--------|
| `common-lib` | DTO/API chung, exception, Excel, OpenFeign client gọi warehouse (`com.common.client.warehouse`) |
| `eureka-server` | Service discovery |
| `api-gateway` | Spring Cloud Gateway, route tới các service |
| `auth-service` | Đăng ký / đăng nhập / JWT |
| `product-service` | Sản phẩm, danh mục, nhà cung cấp |
| `warehouse-service` | Kho, vị trí, tồn kho |
| `inbound-service` | Đơn nhập, putaway; gọi warehouse qua Feign |
| `outbound-service` | Đơn xuất; gọi warehouse qua Feign |

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
