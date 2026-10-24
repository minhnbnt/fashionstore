package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkUpdatePriceRequest(
    @Schema(description = "Danh sách ID sản phẩm cần đổi giá (tối đa 100)")
    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 products at once")
    List<@NotNull(message = "Product ID cannot be null") UUID> productIds,

    @Schema(description = "Chế độ đổi giá: FIXED (giá cố định) hoặc PERCENT (tăng/giảm theo %)", example = "FIXED")
    @NotBlank(message = "Price update mode is required")
    @Pattern(regexp = "^(FIXED|PERCENT)$", message = "Mode must be FIXED or PERCENT")
    String mode,

    @Schema(
        description = "Giá trị: số tiền (VND) nếu mode FIXED, hoặc phần trăm (-90 đến 500) nếu mode PERCENT",
        example = "199000"
    )
    @DecimalMin(value = "-90.0", message = "Value cannot be less than -90")
    @DecimalMax(value = "1000000000.0", message = "Value cannot exceed 1,000,000,000")
    double value
) {
}
