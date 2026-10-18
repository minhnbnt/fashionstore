package com.minhnbnt.fashionstore.model;

/**
 * Nhóm câu hỏi FAQ, thứ tự khai báo là thứ tự hiển thị. Tên trùng CHECK constraint {@code faq_item.category}.
 */
public enum FaqCategory {
    /** Đặt hàng. */
    ORDER,
    /** Thanh toán. */
    PAYMENT,
    /** Vận chuyển. */
    SHIPPING,
    /** Đổi trả và hoàn tiền. */
    RETURN,
    /** Tài khoản. */
    ACCOUNT,
    /** Khác. */
    OTHER
}
