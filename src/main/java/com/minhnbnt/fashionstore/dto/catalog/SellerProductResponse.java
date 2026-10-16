package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerProductResponse(
    UUID id,
    UUID shopId,
    @Nullable UUID categoryId,
    String name,
    String slug,
    String description,
    String status,
    int totalStock,
    long minPrice,
    long maxPrice,
    @Nullable String defaultImageKey,
    List<VariantResponse> variants,
    Instant createdAt,
    Instant updatedAt
) {
}
