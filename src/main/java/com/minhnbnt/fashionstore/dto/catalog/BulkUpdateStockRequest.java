package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkUpdateStockRequest(
    @Schema(description = "Danh sách ID sản phẩm cần cập nhật tồn kho (tối đa 100)")
    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 products at once")
    List<@NotNull(message = "Product ID cannot be null") UUID> productIds,

    @Schema(
        description = "Chế độ cập nhật: FIXED (gán tồn cố định) hoặc DELTA (cộng thêm hoặc trừ đi)",
        example = "FIXED"
    )
    @NotBlank(message = "Stock update mode is required")
    @Pattern(regexp = "^(FIXED|DELTA)$", message = "Mode must be FIXED or DELTA")
    String mode,

    @Schema(
        description = "Số lượng tồn kho: nếu FIXED >= 0, nếu DELTA có thể âm hoặc dương (-100000 đến 100000)",
        example = "50"
    )
    @Min(value = -100000, message = "Quantity cannot be less than -100,000")
    @Max(value = 100000, message = "Quantity cannot exceed 100,000")
    int quantity
) {
}
