package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record UpdateVariantRequest(
    @Schema(description = "Id variant cần sửa (để null nếu thêm biến thể mới)", nullable = true)
    @Nullable
    UUID id,

    @Schema(description = "Mã SKU", nullable = true)
    @Nullable
    String sku,

    @Schema(description = "Kích cỡ", nullable = true)
    @Nullable
    String size,

    @Schema(description = "Màu sắc", nullable = true)
    @Nullable
    String color,

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
    public UpdateVariantRequest(
        UUID id,
        @Nullable Long price,
        @Nullable Long compareAtPrice,
        @Nullable Integer stockQuantity,
        @Nullable Boolean defaultVariant
    ) {
        this(id, null, null, null, price, compareAtPrice, stockQuantity, defaultVariant);
    }
}
