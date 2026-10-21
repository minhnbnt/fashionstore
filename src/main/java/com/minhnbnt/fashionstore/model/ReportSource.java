package com.minhnbnt.fashionstore.model;

/**
 * Nguồn báo cáo sản phẩm vi phạm.
 */
public enum ReportSource {
    /** Người mua bấm "Báo cáo sản phẩm". */
    USER,
    /** Hệ thống tự gắn cờ khi tên/mô tả chứa từ khóa cấm. */
    SYSTEM
}
