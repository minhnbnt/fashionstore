package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReviewMediaResponse(
    @Schema(description = "URL public truy cập media") String url,
    @Schema(description = "Loại media: IMAGE hoặc VIDEO") String kind
) {
}
