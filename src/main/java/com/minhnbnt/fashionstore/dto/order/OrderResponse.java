package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

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

    @Schema(description = "Id dia chi da luu, null neu nhap tay", nullable = true)
    @Nullable
    UUID addressId,

    List<OrderItemResponse> items,

    Instant createdAt
) {
}
