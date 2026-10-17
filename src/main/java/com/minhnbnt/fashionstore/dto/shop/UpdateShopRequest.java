package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateShopRequest(
    @Schema(description = "null = giữ nguyên", nullable = true)
    @Size(max = 200)
    @Nullable
    String name,

    @Schema(description = "null = giữ nguyên", nullable = true)
    @Size(max = 2000)
    @Nullable
    String description,

    @Schema(description = "null = giữ nguyên", nullable = true)
    @Nullable
    String logoKey,

    @Schema(description = "null = giữ nguyên", nullable = true)
    @Nullable
    String bannerKey
) {
}
