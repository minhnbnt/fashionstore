package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SaveDraftLayoutRequest(
    @Schema(description = "JSON layout kéo-thả cấu hình các block (banner, product grid, collection, v.v.)", example = "[{\"type\":\"BANNER_CAROUSEL\",\"order\":0}]")
    @NotBlank
    String layoutJson
) {
}
