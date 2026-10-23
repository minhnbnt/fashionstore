package com.minhnbnt.fashionstore.model;

/**
 * Loại ngày trong lịch nghỉ lễ. Tên trùng CHECK constraint {@code public_holiday.kind}.
 */
public enum HolidayKind {
    /** Ngày nghỉ: không tính vào SLA xử lý đơn. */
    HOLIDAY,
    /** Ngày đi làm bù: tính vào SLA kể cả khi rơi vào Chủ nhật. */
    WORKING_DAY
}
