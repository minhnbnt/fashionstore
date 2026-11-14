package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ImageResponse(
    @Schema UUID id,
    @Schema(nullable = true) @Nullable UUID variantId,
    @Schema(description = "URL public của ảnh") String url,
    @Schema(description = "Id bản ghi upload gốc — FE echo lại khi cập nhật bộ ảnh") UUID uploadId,
    @Schema Integer sortOrder
) {
}
