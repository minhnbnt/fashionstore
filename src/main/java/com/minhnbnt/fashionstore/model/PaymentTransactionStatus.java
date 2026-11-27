package com.minhnbnt.fashionstore.model;

/**
 * Trạng thái phiên giao dịch thanh toán trực tuyến.
 */
public enum PaymentTransactionStatus {
    PENDING,
    SUCCESS,
    UNDERPAID,
    FAILED,
    EXPIRED
}
