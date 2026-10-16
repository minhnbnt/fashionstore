package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnRequestResponse(
    UUID id,
    UUID orderId,
    UUID shopId,
    UUID buyerId,
    String reason,
    @Nullable String description,
    List<String> evidenceKeys,
    String status,
    @Nullable String resolution,
    @Nullable Long refundAmount,
    @Nullable String sellerNote,
    Instant createdAt,
    Instant updatedAt
) {
}
