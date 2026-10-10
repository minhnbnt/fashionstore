package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record UpdateVariantRequest(
    @Schema(description = "Id variant cần sửa")
    UUID id,

    @Schema(description = "Giá mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Min(0)
    Long price,

    @Schema(description = "Giá gốc mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Min(0)
    Long compareAtPrice,

    @Schema(description = "Tồn kho mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Min(0)
    Integer stockQuantity,

    @Schema(description = "Đặt làm default, null = giữ nguyên", nullable = true)
    @Nullable
    Boolean defaultVariant
) {
}
