package com.minhnbnt.fashionstore.model;

public enum ProductStatus {
    /**
     * Sản phẩm mới chờ Admin phê duyệt trước khi hiển thị công khai (áp dụng cho shop chưa uy tín).
     */
    PENDING,
    ACTIVE,
    HIDDEN,
    DELETED,
    /**
     * Admin gỡ vì vi phạm chính sách sàn. Shop không tự đăng bán lại được, chỉ admin khôi phục.
     */
    BANNED
}
