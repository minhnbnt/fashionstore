-- Server tự tính phí ship (công thức phẳng): phí cơ bản mỗi đơn + phí mỗi món vượt shipping.base_items.
-- Mặc định giữ đúng mức FE đang tính cứng trước đây (30.000đ, không cộng theo món).

INSERT INTO system_setting (key, value) VALUES
    ('shipping.base_fee', '30000'),
    ('shipping.extra_item_fee', '0')
ON CONFLICT (key) DO NOTHING;
