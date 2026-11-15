package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ImageSearchHit(
    @Schema UUID id,
    @Schema String name,
    @Schema String slug,
    @Schema(description = "Giá variant thấp nhất (VND)", nullable = true) @Nullable Long minPrice,
    @Schema(description = "URL ảnh đại diện", nullable = true) @Nullable String defaultImageUrl,
    @Schema(description = "Độ giống 0..1, càng lớn càng giống") double similarity
) {
}
