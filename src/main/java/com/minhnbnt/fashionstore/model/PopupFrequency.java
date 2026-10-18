package com.minhnbnt.fashionstore.model;

/**
 * Tần suất hiện popup, đếm ở trình duyệt. Tên trùng CHECK constraint {@code site_popup.frequency}.
 */
public enum PopupFrequency {
    /** Mỗi lần vào trang chủ. */
    EVERY_VISIT,
    /** Một lần mỗi phiên (đóng tab là reset). */
    ONCE_PER_SESSION,
    /** Một lần mỗi ngày. */
    ONCE_PER_DAY,
    /** Một lần duy nhất trên trình duyệt. */
    ONCE
}
