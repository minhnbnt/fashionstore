package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    @Schema UUID id,
    @Schema UUID productId,
    @Schema(description = "Ten hien thi luc viet, doi ten sau khong viet lai lich su") String buyerDisplayName,
    @Schema int stars,
    @Schema(nullable = true) @Nullable String comment,
    @Schema Instant createdAt,
    @Schema Instant updatedAt
) {
}
