package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminOrderSummaryResponse(
    @Schema(description = "Tổng đơn khớp bộ lọc")
    long total,

    @Schema(description = "Đơn chờ shop xác nhận")
    long pending,

    @Schema(description = "Đơn đã xác nhận, chờ giao vận chuyển")
    long confirmed,

    @Schema(description = "Đơn đang giao")
    long shipping,

    @Schema(description = "Đơn đã giao")
    long delivered,

    @Schema(description = "Đơn đã hủy")
    long cancelled,

    @Schema(description = "Chờ xác nhận quá 24 giờ")
    long confirmLate,

    @Schema(description = "Đã xác nhận nhưng chưa bàn giao vận chuyển sau 48 giờ kể từ lúc đặt")
    long handoverLate,

    @Schema(description = "Đang giao quá 7 ngày")
    long deliveryLate,

    @Schema(description = "Đã giao, đang có yêu cầu trả hàng chưa xong")
    long returnOpen
) {
}
