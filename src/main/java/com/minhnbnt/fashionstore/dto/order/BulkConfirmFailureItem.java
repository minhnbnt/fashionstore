package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record BulkConfirmFailureItem(
    @Schema(description = "ID của đơn hàng thất bại")
    UUID orderId,

    @Schema(description = "Mã đơn hàng", example = "ORD-AB12CD34")
    String code,

    @Schema(
        description = "Lý do thất bại",
        example = "Order confirmation deadline (24h) has expired; order was cancelled by system"
    )
    String reason
) {
}
