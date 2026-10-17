package com.minhnbnt.fashionstore.model;

/**
 * Loại vi phạm khi admin gỡ sản phẩm. Tên trùng CHECK constraint {@code product.violation_type}.
 */
public enum ProductViolationType {
    /** Hàng cấm / hạn chế kinh doanh. */
    PROHIBITED,
    /** Hàng giả, hàng nhái. */
    COUNTERFEIT,
    /** Vi phạm sở hữu trí tuệ (thương hiệu, hình ảnh). */
    IP_INFRINGEMENT,
    /** Thông tin, hình ảnh, giá sai lệch hoặc gây hiểu nhầm. */
    MISLEADING,
    /** Đăng sai danh mục. */
    WRONG_CATEGORY,
    /** Đăng trùng lặp, spam từ khóa. */
    SPAM,
    /** Khác, ghi rõ trong lý do. */
    OTHER
}
