package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public record CancelOrderRequest(
    @Schema(description = "Lý do hủy đơn", example = "OUT_OF_STOCK")
    @NotBlank
    String reason,

    @Schema(description = "Ghi chú thêm", nullable = true)
    @Nullable
    String note
) {
}
