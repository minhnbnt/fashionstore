package com.minhnbnt.fashionstore.features.settings;

/**
 * Cấu hình chung đã parse kiểu; tiền là VND.
 */
public record SystemSettings(
    String siteName,
    String hotline,
    String supportEmail,
    long freeShippingThreshold,
    int baseItems,
    int paymentHoldMinutes,
    int autoCompleteDays,
    long codMinTotal,
    long codMaxTotal
) {
}
