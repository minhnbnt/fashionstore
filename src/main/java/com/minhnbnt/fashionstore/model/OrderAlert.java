package com.minhnbnt.fashionstore.model;

/**
 * Cảnh báo SLA của đơn trên màn giám sát admin. Mỗi cảnh báo gắn với đúng 1 trạng thái nên 1 đơn có tối đa 1 cảnh báo.
 * Mốc tính theo ngày làm việc (không tính Chủ nhật, ngày lễ) như Shopee/Lazada, xem {@code OrderSlaPolicy}.
 */
public enum OrderAlert {
    /** PENDING quá 1 ngày làm việc kể từ lúc đặt: shop chưa xác nhận. */
    CONFIRM_LATE,
    /** CONFIRMED quá 2 ngày làm việc kể từ lúc đặt: chưa giao cho vận chuyển. */
    HANDOVER_LATE,
    /** SHIPPING quá 7 ngày làm việc kể từ lúc gửi: giao hàng quá lâu. */
    DELIVERY_LATE,
    /** DELIVERED có yêu cầu trả hàng REQUESTED/APPROVED chưa xong. */
    RETURN_OPEN
}
