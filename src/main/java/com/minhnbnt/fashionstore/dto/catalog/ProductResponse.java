package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
    @Schema UUID id,
    @Schema UUID shopId,
    @Schema(description = "Danh mục, null nếu chưa gán", nullable = true) @Nullable UUID categoryId,
    @Schema String name,
    @Schema String slug,
    @Schema String description,
    @Schema String status,
    @Schema List<VariantResponse> variants,
    @Schema List<ImageResponse> images,
    @Schema Instant updatedAt,
    @Schema(description = "Diem trung binh, null khi chua ai danh gia", nullable = true)
    @Nullable Double averageRating,
    @Schema(description = "So luot danh gia") long reviewCount
) {
}
