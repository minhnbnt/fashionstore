package com.minhnbnt.fashionstore.model;

public enum ReportStatus {
    /** Chờ admin xử lý. */
    OPEN,
    /** Admin đã gỡ sản phẩm. */
    ACTION_TAKEN,
    /** Admin xác minh không vi phạm. */
    DISMISSED
}
