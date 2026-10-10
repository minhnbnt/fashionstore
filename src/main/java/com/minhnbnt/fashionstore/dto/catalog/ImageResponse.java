package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ImageResponse(
    @Schema UUID id,
    @Schema(nullable = true) @Nullable UUID variantId,
    @Schema(description = "S3 key, frontend tự ghép base URL") String s3Key,
    @Schema Integer sortOrder
) {
}
