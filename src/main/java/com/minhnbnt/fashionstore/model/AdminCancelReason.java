package com.minhnbnt.fashionstore.model;

/**
 * Lý do admin hủy can thiệp đơn hàng. Lưu vào {@code orders.cancel_reason}.
 */
public enum AdminCancelReason {
    /** Nghi gian lận, đơn ảo, buff đơn. */
    FRAUD_SUSPECTED,
    /** Shop không xử lý đơn quá hạn. */
    SELLER_UNRESPONSIVE,
    /** Người mua yêu cầu hủy qua CSKH. */
    BUYER_REQUEST,
    /** Đơn chứa hàng vi phạm chính sách. */
    PROHIBITED_ITEM,
    /** Khác, ghi rõ trong ghi chú. */
    OTHER
}
