package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public record ApproveReturnRequest(
    @Schema(description = "Phương án giải quyết (REFUND, EXCHANGE, PARTIAL_REFUND)", example = "REFUND")
    @NotBlank
    String resolution,

    @Schema(description = "Số tiền hoàn lại (VND) nếu refund", nullable = true)
    @Nullable
    Long refundAmount,

    @Schema(description = "Ghi chú của seller", nullable = true)
    @Nullable
    String sellerNote
) {
}
