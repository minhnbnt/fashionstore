package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tổng kết trạng thái đơn hàng và giá trị tương ứng")
public record OrderStatusSummaryResponse(
    @Schema(description = "Số đơn hàng hoàn thành thành công", example = "85")
    long completedOrders,

    @Schema(description = "Tổng giá trị đơn thành công (VND)", example = "35000000")
    long completedValue,

    @Schema(description = "Số đơn hàng đã hủy", example = "5")
    long cancelledOrders,

    @Schema(description = "Tổng giá trị đơn bị hủy (VND)", example = "1800000")
    long cancelledValue,

    @Schema(description = "Số đơn đổi / trả", example = "2")
    long returnedOrders,

    @Schema(description = "Tổng giá trị đơn đổi / trả hoàn tiền (VND)", example = "650000")
    long returnedValue
) {}
