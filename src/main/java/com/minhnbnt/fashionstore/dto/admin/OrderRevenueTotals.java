package com.minhnbnt.fashionstore.dto.admin;

/**
 * GMV = tổng giá trị đơn chưa hủy; collected = tiền COD đã thu (payment_status = COLLECTED).
 */
public record OrderRevenueTotals(long grossMerchandiseValue, long collected) {
}
