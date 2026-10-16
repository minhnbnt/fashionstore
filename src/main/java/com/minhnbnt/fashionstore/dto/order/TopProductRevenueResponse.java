package com.minhnbnt.fashionstore.dto.order;

import java.util.UUID;

public record TopProductRevenueResponse(
    UUID productId,
    String productName,
    int soldQuantity,
    long totalRevenue
) {
}
