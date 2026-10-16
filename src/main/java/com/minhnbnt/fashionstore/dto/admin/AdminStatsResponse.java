package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminStatsResponse(
    @Schema(description = "Tổng tài khoản")
    long totalUsers,

    @Schema(description = "Tài khoản đang bị khóa")
    long lockedUsers,

    @Schema(description = "Tài khoản role CUSTOMER")
    long customerUsers,

    @Schema(description = "Tài khoản role SHOP (chủ shop đã được duyệt)")
    long sellerUsers,

    @Schema(description = "Tài khoản role ADMIN")
    long adminUsers,

    @Schema(description = "Tổng shop")
    long totalShops,

    @Schema(description = "Shop chờ duyệt")
    long pendingShops,

    @Schema(description = "Shop đang hoạt động")
    long approvedShops,

    @Schema(description = "Shop bị tạm khóa")
    long suspendedShops,

    @Schema(description = "Shop bị từ chối")
    long rejectedShops,

    @Schema(description = "Tổng đơn hàng")
    long totalOrders,

    @Schema(description = "Đơn chờ shop xác nhận")
    long pendingOrders,

    @Schema(description = "Đơn đã xác nhận, chờ giao")
    long confirmedOrders,

    @Schema(description = "Đơn đang giao")
    long shippingOrders,

    @Schema(description = "Đơn đã giao")
    long deliveredOrders,

    @Schema(description = "Đơn đã hủy")
    long cancelledOrders,

    @Schema(description = "Tổng giá trị đơn chưa hủy (VND)")
    long grossMerchandiseValue,

    @Schema(description = "Doanh thu COD đã thu (VND)")
    long collectedRevenue,

    @Schema(description = "Số liệu theo ngày (giờ Việt Nam), 14 ngày gần nhất, cũ trước mới sau, ngày cuối là hôm nay")
    List<AdminDailyStatResponse> dailyTrend
) {
}
