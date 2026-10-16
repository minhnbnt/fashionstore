package com.minhnbnt.fashionstore.dto.order;

public record RevenueSummaryResponse(
    long totalRevenue,
    long totalOrders,
    long avgOrderValue,
    long walletBalance,
    long holdingBalance
) {
}
