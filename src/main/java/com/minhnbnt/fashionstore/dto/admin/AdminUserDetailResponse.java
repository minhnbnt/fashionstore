package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminUserDetailResponse(
    AdminUserResponse user,

    @Schema(description = "Shop user làm OWNER/STAFF")
    List<ShopResponse> shops,

    @Schema(description = "Số đơn user đã đặt (mọi trạng thái)")
    long orderCount,

    @Schema(description = "Lịch sử khóa/mở khóa, cũ nhất trước")
    List<StatusHistoryResponse> history
) {
}
