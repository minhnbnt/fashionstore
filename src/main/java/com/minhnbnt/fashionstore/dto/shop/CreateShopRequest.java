package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateShopRequest(
    @Schema(example = "Shop An")
    @NotBlank
    @Size(max = 200)
    String name,

    @Schema(description = "Slug duy nhất toàn sàn, chữ thường + số + gạch ngang", example = "shop-an")
    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$")
    String slug,

    @Schema(nullable = true)
    @Size(max = 2000)
    @Nullable
    String description,

    @Schema(description = "S3 key logo, null nếu chưa có", nullable = true)
    @Nullable
    String logoKey,

    @Schema(description = "S3 key banner, null nếu chưa có", nullable = true)
    @Nullable
    String bannerKey
) {
}
