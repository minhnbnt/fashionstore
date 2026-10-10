package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PresignResponse(
    @Schema(description = "S3 key để gắn vào product sau khi upload") String key,
    @Schema(description = "URL upload trực tiếp") String url,
    @Schema Instant expiresAt
) {
}
