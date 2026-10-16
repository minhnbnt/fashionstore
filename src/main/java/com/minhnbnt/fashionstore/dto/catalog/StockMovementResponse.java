package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
    UUID id,
    UUID variantId,
    UUID shopId,
    int delta,
    String reason,
    @Nullable UUID referenceId,
    Instant createdAt
) {
}
