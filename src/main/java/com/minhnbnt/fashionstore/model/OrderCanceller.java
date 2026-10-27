package com.minhnbnt.fashionstore.model;

/**
 * Ai hủy đơn. Tên trùng CHECK constraint {@code orders.cancelled_by}.
 */
public enum OrderCanceller {
    BUYER,
    SELLER,
    ADMIN,
    SYSTEM
}
