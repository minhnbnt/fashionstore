package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "So sánh hiệu suất doanh thu giữa kỳ hiện tại và kỳ trước")
public record PeriodComparisonResponse(
    @Schema(description = "Doanh thu kỳ hiện tại (VND)", example = "12000000")
    long currentRevenue,

    @Schema(description = "Doanh thu kỳ trước (VND)", example = "10000000")
    long previousRevenue,

    @Schema(description = "Tỷ lệ tăng trưởng (-1.0 đến 1.0+)", example = "0.20")
    double changePercent,

    @Schema(description = "Số đơn hàng kỳ hiện tại", example = "45")
    long currentOrders,

    @Schema(description = "Số đơn hàng kỳ trước", example = "38")
    long previousOrders
) {}
