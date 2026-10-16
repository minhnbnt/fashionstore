package com.minhnbnt.fashionstore.dto.order;

public record DailyRevenueResponse(
    String date,
    long revenue,
    int orderCount
) {
}
