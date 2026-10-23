package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record BulkUpdateStockRequest(
    @Schema(description = "Danh sách ID sản phẩm cần cập nhật tồn kho (tối đa 100)")
    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 products at once")
    List<@NotNull(message = "Product ID cannot be null") UUID> productIds,

    @Schema(description = "Số lượng tồn kho cố định mới gán cho tất cả biến thể", nullable = true, example = "50")
    @Nullable
    @Min(value = 0, message = "Fixed stock cannot be negative")
    Integer fixedStock,

    @Schema(description = "Số lượng tồn kho cộng thêm (+) hoặc trừ đi (-) cho mỗi biến thể", nullable = true, example = "10")
    @Nullable
    Integer deltaStock
) {
    @AssertTrue(message = "Either fixedStock or deltaStock must be provided, but not both")
    public boolean isValidStockInput() {
        if (fixedStock != null && deltaStock != null) {
            return false;
        }
        if (fixedStock == null && deltaStock == null) {
            return false;
        }
        return true;
    }
}
