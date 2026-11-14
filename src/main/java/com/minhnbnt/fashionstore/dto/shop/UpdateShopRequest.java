package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record UpdateShopRequest(
    @Schema(description = "null = giữ nguyên", nullable = true)
    @Size(max = 200)
    @Nullable
    String name,

    @Schema(description = "null = giữ nguyên", nullable = true)
    @Size(max = 2000)
    @Nullable
    String description,

    @Schema(description = "Id upload logo mới, null = giữ nguyên", nullable = true)
    @Nullable
    UUID logoUploadId,

    @Schema(description = "Id upload banner mới, null = giữ nguyên", nullable = true)
    @Nullable
    UUID bannerUploadId
) {
}
