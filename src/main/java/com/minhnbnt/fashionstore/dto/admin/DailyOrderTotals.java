package com.minhnbnt.fashionstore.dto.admin;

/**
 * Số đơn và tổng giá trị đơn chưa hủy trong một ngày.
 */
public record DailyOrderTotals(long orders, long value) {
}
