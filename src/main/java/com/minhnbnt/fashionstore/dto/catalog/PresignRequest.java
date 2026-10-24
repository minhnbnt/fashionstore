package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignRequest(
    @Schema(description = "Chỉ nhận image/* hoặc video/*", example = "image/jpeg")
    @NotBlank
    @Pattern(regexp = "^(image|video)/[a-zA-Z0-9.+-]+$")
    String contentType
) {
}
