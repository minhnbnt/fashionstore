package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record VariantResponse(
    @Schema UUID id,
    @Schema String sku,
    @Schema String size,
    @Schema String color,
    @Schema Long price,
    @Schema(nullable = true) @Nullable Long compareAtPrice,
    @Schema Integer stockQuantity,
    @Schema Boolean defaultVariant,
    @Schema Instant updatedAt
) {
}
