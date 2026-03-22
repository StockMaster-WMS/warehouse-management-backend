-- ============================================================
-- warehouse-service: V2__seed_warehouses.sql
-- Seed realistic warehouse data for UI testing
-- ============================================================

INSERT INTO warehouses (id, code, name, address, timezone, is_active, created_at)
VALUES
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a11', 'WH-HCM-DC01', 'Kho Tong HCM DC01', 'Khu Che Xuat Tan Thuan, Quan 7, TP HCM', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '120 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a12', 'WH-HCM-CFS02', 'Kho CFS HCM 02', 'Duong Nguyen Van Linh, Binh Chanh, TP HCM', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '95 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a13', 'WH-BD-DC01', 'Kho Binh Duong DC01', 'KCN VSIP 1, Thuan An, Binh Duong', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '90 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a14', 'WH-DN-DC01', 'Kho Da Nang DC01', 'Duong so 3 KCN Hoa Khanh, Da Nang', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '80 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a15', 'WH-HN-DC01', 'Kho Tong Ha Noi DC01', 'KCN Quang Minh, Me Linh, Ha Noi', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '75 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a16', 'WH-HP-PORT01', 'Kho Cang Hai Phong 01', 'Dinh Vu, Hai An, Hai Phong', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '60 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a17', 'WH-CT-R01', 'Kho Can Tho Regional 01', 'KCN Tra Noc, Binh Thuy, Can Tho', 'Asia/Ho_Chi_Minh', TRUE, now() - interval '45 days'),
    ('0195b71b-6f6c-7d91-85c8-5be26e6f2a18', 'WH-HCM-RET01', 'Kho Hang Loi HCM', 'Le Van Khuong, Quan 12, TP HCM', 'Asia/Ho_Chi_Minh', FALSE, now() - interval '30 days')
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    address = EXCLUDED.address,
    timezone = EXCLUDED.timezone,
    is_active = EXCLUDED.is_active;
