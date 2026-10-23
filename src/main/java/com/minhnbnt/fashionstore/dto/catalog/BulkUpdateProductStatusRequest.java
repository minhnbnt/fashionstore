package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkUpdateProductStatusRequest(
    @Schema(description = "Danh sách ID sản phẩm cần cập nhật trạng thái (tối đa 100)")
    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 products at once")
    List<@NotNull(message = "Product ID cannot be null") UUID> productIds,

    @Schema(description = "Trạng thái mới: ACTIVE hoặc HIDDEN", example = "HIDDEN")
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(ACTIVE|HIDDEN)$", message = "Status must be either ACTIVE or HIDDEN")
    String status
) {
}
