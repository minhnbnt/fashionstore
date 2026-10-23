package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record BulkUpdatePriceRequest(
    @Schema(description = "Danh sách ID sản phẩm cần đổi giá (tối đa 100)")
    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 products at once")
    List<@NotNull(message = "Product ID cannot be null") UUID> productIds,

    @Schema(description = "Giá cố định mới cho tất cả biến thể (VND)", nullable = true, example = "199000")
    @Nullable
    @Positive(message = "Fixed price must be positive if provided")
    Long fixedPrice,

    @Schema(description = "Phần trăm thay đổi giá (-90% đến +500%)", nullable = true, example = "-10.0")
    @Nullable
    @Min(value = -90, message = "Percent change cannot be less than -90%")
    @Max(value = 500, message = "Percent change cannot exceed +500%")
    Double percentChange
) {
    @AssertTrue(message = "Either fixedPrice or percentChange must be provided, but not both")
    public boolean isValidPriceInput() {
        if (fixedPrice != null && percentChange != null) {
            return false;
        }
        if (fixedPrice == null && percentChange == null) {
            return false;
        }
        return true;
    }
}
