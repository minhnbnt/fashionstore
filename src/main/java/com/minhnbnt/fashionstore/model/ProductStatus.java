package com.minhnbnt.fashionstore.model;

public enum ProductStatus {
    ACTIVE,
    HIDDEN,
    DELETED,
    /**
     * Admin gỡ vì vi phạm chính sách sàn. Shop không tự đăng bán lại được, chỉ admin khôi phục.
     */
    BANNED
}
