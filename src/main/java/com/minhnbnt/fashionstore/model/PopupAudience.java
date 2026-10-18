package com.minhnbnt.fashionstore.model;

/**
 * Đối tượng thấy popup. Tên trùng CHECK constraint {@code site_popup.audience}.
 */
public enum PopupAudience {
    ALL,
    /** Chưa đăng nhập. */
    GUEST,
    /** Đã đăng nhập. */
    MEMBER
}
