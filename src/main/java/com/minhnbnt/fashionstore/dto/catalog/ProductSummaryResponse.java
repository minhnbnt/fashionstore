package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ProductSummaryResponse(
    @Schema UUID id,
    @Schema UUID shopId,
    @Schema String name,
    @Schema String slug,
    @Schema Long minPrice,
    @Schema(description = "S3 key ảnh đại diện, null nếu chưa có", nullable = true)
    @Nullable String defaultImageKey,
    @Schema(description = "Điểm BM25") Double rank
) {
}
