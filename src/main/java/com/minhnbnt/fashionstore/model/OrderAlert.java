package com.minhnbnt.fashionstore.model;

/**
 * Cảnh báo SLA của đơn trên màn giám sát admin. Mỗi cảnh báo gắn với đúng 1 trạng thái nên 1 đơn có tối đa 1 cảnh báo.
 * Mốc giờ tham khảo Shopee/Lazada: 2 ngày từ lúc đặt phải bàn giao vận chuyển, 7 ngày phải tới nơi.
 */
public enum OrderAlert {
    /** PENDING quá 24 giờ kể từ lúc đặt: shop chưa xác nhận. */
    CONFIRM_LATE,
    /** CONFIRMED quá 48 giờ kể từ lúc đặt: chưa giao cho vận chuyển. */
    HANDOVER_LATE,
    /** SHIPPING quá 7 ngày kể từ lúc gửi: giao hàng quá lâu. */
    DELIVERY_LATE,
    /** DELIVERED có yêu cầu trả hàng REQUESTED/APPROVED chưa xong. */
    RETURN_OPEN
}
