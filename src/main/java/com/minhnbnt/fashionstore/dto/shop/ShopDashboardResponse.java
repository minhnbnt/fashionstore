package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông số thống kê tổng quan của Shop")
public record ShopDashboardResponse(
    @Schema(description = "Doanh thu hôm nay (VND)", example = "3720000")
    long todayRevenue,

    @Schema(description = "Số đơn hàng hôm nay", example = "14")
    long todayOrders,

    @Schema(description = "Số đơn hàng cần xử lý", example = "5")
    long pendingOrders,

    @Schema(description = "Lượt truy cập shop hôm nay", example = "1280")
    long shopVisits,

    @Schema(description = "Doanh thu hôm qua (VND)", example = "3140000")
    long yesterdayRevenue,

    @Schema(description = "Số đơn hàng hôm qua", example = "10")
    long yesterdayOrders
) {}
