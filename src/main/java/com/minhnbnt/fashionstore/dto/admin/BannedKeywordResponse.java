package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record BannedKeywordResponse(
    UUID id,

    @Schema(description = "Đã chuẩn hóa chữ thường", example = "replica")
    String keyword,

    @Schema(example = "COUNTERFEIT")
    String violationType,

    @Schema(nullable = true)
    @Nullable
    String createdByEmail,

    Instant createdAt
) {
}
