package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkDeleteProductsRequest(
    @Schema(description = "Danh sách ID sản phẩm cần xóa mềm (tối đa 100)")
    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(max = 100, message = "Cannot delete more than 100 products at once")
    List<@NotNull(message = "Product ID cannot be null") UUID> productIds
) {
}
