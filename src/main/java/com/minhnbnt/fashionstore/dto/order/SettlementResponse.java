package com.minhnbnt.fashionstore.dto.order;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(
    UUID id,
    UUID shopId,
    UUID orderId,
    long grossAmount,
    long platformFee,
    long netAmount,
    String status,
    Instant holdUntil,
    @Nullable Instant releasedAt,
    Instant createdAt
) {
}
