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
    @Schema(description = "So luot danh gia") long reviewCount,
    @Schema(description = "URL public video giới thiệu", nullable = true) @Nullable String videoUrl,
    @Schema(description = "URL public ảnh garment thử đồ ảo", nullable = true) @Nullable String garmentUrl,
    @Schema(description = "Id upload video — FE echo lại khi cập nhật để giữ nguyên, null = chưa có", nullable = true)
    @Nullable UUID videoUploadId,
    @Schema(description = "Id upload garment — FE echo lại khi cập nhật để giữ nguyên, null = chưa có", nullable = true)
    @Nullable UUID garmentUploadId,
    @Schema(description = "Hỗ trợ thử đồ ảo") Boolean tryOnEnabled,
    @Schema(nullable = true) @Nullable String material,
    @Schema(nullable = true) @Nullable String origin,
    @Schema(nullable = true) @Nullable String careInstructions,
    @Schema(nullable = true) @Nullable String sizeGuide,
    @Schema(nullable = true) @Nullable Integer weightGrams,
    @Schema(nullable = true) @Nullable Integer lengthCm,
    @Schema(nullable = true) @Nullable Integer widthCm,
    @Schema(nullable = true) @Nullable Integer heightCm
) {
    public ProductResponse(
        UUID id,
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        String status,
        List<VariantResponse> variants,
        List<ImageResponse> images,
        Instant updatedAt,
        @Nullable Double averageRating,
        long reviewCount
    ) {
        this(
            id, shopId, categoryId, name, slug, description, status,
            variants, images, updatedAt, averageRating, reviewCount,
            null, null, null, null, false, null, null, null, null, null, null, null, null
        );
    }
}
