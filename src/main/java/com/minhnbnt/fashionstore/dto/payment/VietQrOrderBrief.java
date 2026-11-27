package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record VietQrOrderBrief(
    @Schema(description = "ID đơn hàng")
    UUID orderId,

    @Schema(description = "Mã đơn hàng hiển thị", example = "ORD-20261126-0001")
    String orderCode,

    @Schema(description = "ID cửa hàng")
    UUID shopId,

    @Schema(description = "Tên cửa hàng", example = "Highlight Studio")
    String shopName,

    @Schema(description = "Tổng tiền đơn hàng (VND)", example = "230000")
    long total,

    @Schema(description = "Trạng thái đơn hàng", example = "AWAITING_PAYMENT")
    String orderStatus,

    @Schema(description = "Trạng thái thanh toán của đơn", example = "UNPAID")
    String paymentStatus
) {
}
