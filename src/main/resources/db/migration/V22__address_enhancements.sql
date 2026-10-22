-- V22__address_enhancements.sql
-- Nâng cấp sổ địa chỉ: người nhận, số điện thoại, địa chỉ chi tiết, nhãn phân loại (HOME, OFFICE, OTHER).

ALTER TABLE address
    ADD COLUMN recipient_name TEXT,
    ADD COLUMN phone_number TEXT,
    ADD COLUMN detail_address TEXT,
    ADD COLUMN label TEXT NOT NULL DEFAULT 'HOME'
        CHECK (label IN ('HOME', 'OFFICE', 'OTHER'));

-- Cập nhật dữ liệu cũ nếu có
UPDATE address
SET recipient_name = coalesce((SELECT display_name FROM user_profile WHERE user_profile.id = address.user_id), 'Khách hàng'),
    phone_number = '0900000000',
    detail_address = trim(both ' ' from concat_ws(' ', house_number, street))
WHERE recipient_name IS NULL;

ALTER TABLE address
    ALTER COLUMN recipient_name SET DEFAULT 'Khách hàng',
    ALTER COLUMN phone_number SET DEFAULT '0900000000',
    ALTER COLUMN recipient_name SET NOT NULL,
    ALTER COLUMN phone_number SET NOT NULL;
