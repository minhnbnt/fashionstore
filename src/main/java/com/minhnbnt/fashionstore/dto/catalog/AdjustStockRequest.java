package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record AdjustStockRequest(
    @Schema(description = "Số lượng tồn kho mới (phải >= 0)", example = "50")
    @NotNull
    Integer newStock,

    @Schema(description = "Lý do điều chỉnh (MANUAL_ADJUST, RESTOCK, DAMAGE, v.v.)", example = "RESTOCK", nullable = true)
    @Nullable
    String reason
) {
}
