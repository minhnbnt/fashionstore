package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,

    String code,

    UUID shopId,

    UUID buyerId,

    @Schema(example = "PENDING")
    String status,

    @Schema(example = "UNPAID")
    String paymentStatus,

    long subtotal,

    long shippingFee,

    long total,

    String shippingName,

    String shippingPhone,

    String shippingAddress,

    List<OrderItemResponse> items,

    Instant createdAt
) {
}
