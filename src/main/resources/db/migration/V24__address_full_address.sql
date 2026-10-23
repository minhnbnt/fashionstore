-- V23__address_full_address.sql
-- Địa chỉ là MỘT chuỗi hoàn chỉnh do frontend build từ API danh mục /api/public/locations.
-- Backend không lưu dữ liệu hành chính (tỉnh/thành, phường/xã) thành các cột riêng nữa.

ALTER TABLE address
    DROP COLUMN house_number,
    DROP COLUMN street,
    DROP COLUMN ward,
    DROP COLUMN district,
    DROP COLUMN city;

ALTER TABLE address RENAME COLUMN detail_address TO full_address;

-- Dọn dev rows placeholder từ V22 (phone '0900000000', detail ghép từ house+street) — không phải địa chỉ thật.
DELETE FROM address;

ALTER TABLE address
    ALTER COLUMN full_address SET NOT NULL;
