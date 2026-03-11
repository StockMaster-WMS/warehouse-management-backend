# warehouse-management-backend

Thư mục này được chuyển thành **thư mục cha (parent)** cho dự án kiến trúc multi-service.

## Giới thiệu

Đây là project Maven dạng **parent/aggregator**, dùng để quản lý các service con trong hệ thống `warehouse-management` theo mô hình multi-module.

## Mục đích

- Quản lý tập trung các service con bằng Maven multi-module.
- Không chứa mã nguồn nghiệp vụ trực tiếp ở cấp parent.

## Cấu trúc gợi ý

```text
warehouse-management-backend/
	pom.xml                  # parent/aggregator
	services/
		inventory-service/
		order-service/
```

## Thêm service mới

1. Tạo thư mục service con (ví dụ: `services/inventory-service`).
2. Tạo `pom.xml` cho service con.
3. Khai báo module trong `pom.xml` của parent:

```xml
<modules>
		<module>services/inventory-service</module>
</modules>
```
