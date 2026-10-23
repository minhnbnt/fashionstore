package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record UpdateVariantRequest(
    @Schema(description = "Id variant cần sửa (để null nếu thêm biến thể mới)", nullable = true)
    @Nullable
    UUID id,

    @Schema(description = "Mã SKU", nullable = true)
    @Nullable
    @Size(max = 100, message = "SKU cannot exceed 100 characters")
    String sku,

    @Schema(description = "Kích cỡ", nullable = true)
    @Nullable
    @Size(max = 50, message = "Size cannot exceed 50 characters")
    String size,

    @Schema(description = "Màu sắc", nullable = true)
    @Nullable
    @Size(max = 50, message = "Color cannot exceed 50 characters")
    String color,

    @Schema(description = "Giá mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Min(value = 0, message = "Price must be non-negative")
    Long price,

    @Schema(description = "Giá gốc mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Min(value = 0, message = "Compare at price must be non-negative")
    Long compareAtPrice,

    @Schema(description = "Tồn kho mới, null = giữ nguyên", nullable = true)
    @Nullable
    @Min(value = 0, message = "Stock quantity must be non-negative")
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
