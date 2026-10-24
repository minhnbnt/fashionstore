package com.minhnbnt.fashionstore.model;

/**
 * Nguồn của 1 ngày trong lịch nghỉ lễ. Tên trùng CHECK constraint {@code public_holiday.source}.
 */
public enum HolidaySource {
    /** Seed sẵn trong migration. */
    SEED,
    /** Đồng bộ từ Calendarific, lần đồng bộ sau được ghi đè/xóa. */
    CALENDARIFIC,
    /** Admin sửa tay, đồng bộ không bao giờ ghi đè. */
    MANUAL
}
