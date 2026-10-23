-- V16__public_holiday.sql
-- Lịch nghỉ lễ cho SLA xử lý đơn: đồng bộ từ Calendarific, admin sửa tay được.

CREATE TABLE public_holiday (
    day DATE PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('HOLIDAY', 'WORKING_DAY')),
    source TEXT NOT NULL CHECK (source IN ('SEED', 'CALENDARIFIC', 'MANUAL')),
    -- false = admin đã bỏ ngày này; giữ dòng để lần đồng bộ sau không thêm lại.
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Lần đồng bộ gần nhất của từng năm.
CREATE TABLE holiday_sync (
    year INT PRIMARY KEY,
    status TEXT NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    message TEXT NOT NULL DEFAULT '',
    holiday_count INT NOT NULL DEFAULT 0,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed theo thông báo của Bộ Nội vụ, để chưa có API key vẫn tính SLA đúng.
INSERT INTO public_holiday (day, name, kind, source) VALUES
    ('2026-01-01', 'Tết Dương lịch', 'HOLIDAY', 'SEED'),
    ('2026-02-14', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-15', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-16', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-17', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-18', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-19', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-20', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-21', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-02-22', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2026-04-27', 'Nghỉ bù Giỗ Tổ Hùng Vương', 'HOLIDAY', 'SEED'),
    ('2026-04-30', 'Ngày Giải phóng miền Nam', 'HOLIDAY', 'SEED'),
    ('2026-05-01', 'Ngày Quốc tế Lao động', 'HOLIDAY', 'SEED'),
    ('2026-09-01', 'Quốc khánh', 'HOLIDAY', 'SEED'),
    ('2026-09-02', 'Quốc khánh', 'HOLIDAY', 'SEED'),
    ('2026-11-24', 'Ngày Văn hóa Việt Nam', 'HOLIDAY', 'SEED'),
    ('2027-01-01', 'Tết Dương lịch', 'HOLIDAY', 'SEED'),
    ('2027-02-04', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-02-05', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-02-06', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-02-07', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-02-08', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-02-09', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-02-10', 'Tết Nguyên đán', 'HOLIDAY', 'SEED'),
    ('2027-04-30', 'Ngày Giải phóng miền Nam', 'HOLIDAY', 'SEED'),
    ('2027-05-01', 'Ngày Quốc tế Lao động', 'HOLIDAY', 'SEED'),
    ('2027-05-03', 'Nghỉ bù Quốc tế Lao động', 'HOLIDAY', 'SEED'),
    ('2027-09-02', 'Quốc khánh', 'HOLIDAY', 'SEED'),
    ('2027-09-03', 'Quốc khánh', 'HOLIDAY', 'SEED'),
    ('2027-11-24', 'Ngày Văn hóa Việt Nam', 'HOLIDAY', 'SEED');
