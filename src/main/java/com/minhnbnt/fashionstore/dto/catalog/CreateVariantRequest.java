package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record CreateVariantRequest(
    @Schema(description = "SKU duy nhất trong shop", example = "AO-THUN-DEN-M")
    @NotBlank
    String sku,

    @Schema(example = "M")
    @NotBlank
    String size,

    @Schema(example = "Đen")
    @NotBlank
    String color,

    @Schema(description = "Giá VND", example = "100000")
    @NotNull
    @Min(0)
    Long price,

    @Schema(description = "Giá gốc để hiện % giảm, null nếu không có", nullable = true)
    @Nullable
    @Min(0)
    Long compareAtPrice,

    @Schema(description = "Tồn kho ban đầu", example = "10")
    @NotNull
    @Min(0)
    Integer stockQuantity,

    @Schema(description = "Đúng 1 variant default mỗi product")
    @NotNull
    Boolean defaultVariant
) {
}
