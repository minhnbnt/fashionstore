package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminStatsResponse(
    @Schema(description = "Tổng tài khoản")
    long totalUsers,

    @Schema(description = "Tài khoản đang bị khóa")
    long lockedUsers,

    @Schema(description = "Tổng shop")
    long totalShops,

    @Schema(description = "Shop chờ duyệt")
    long pendingShops,

    @Schema(description = "Shop đang hoạt động")
    long approvedShops,

    @Schema(description = "Shop bị tạm khóa")
    long suspendedShops,

    @Schema(description = "Shop bị từ chối")
    long rejectedShops
) {
}
