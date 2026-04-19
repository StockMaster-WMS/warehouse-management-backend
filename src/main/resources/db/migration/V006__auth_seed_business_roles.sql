INSERT INTO roles (id, code, name, description, created_at)
VALUES
    (gen_random_uuid(), 'ADMIN', 'Quản trị hệ thống', 'Toàn quyền quản trị hệ thống', now()),
    (gen_random_uuid(), 'WAREHOUSE_MANAGER', 'Quản lý kho', 'Điều phối và giám sát nghiệp vụ kho', now()),
    (gen_random_uuid(), 'WAREHOUSE_STAFF', 'Nhân viên kho', 'Thực hiện nhập, xuất, kiểm kê và xử lý vị trí', now()),
    (gen_random_uuid(), 'REPORT_VIEWER', 'Người xem báo cáo', 'Chỉ xem dashboard và báo cáo', now())
ON CONFLICT (code) DO NOTHING;