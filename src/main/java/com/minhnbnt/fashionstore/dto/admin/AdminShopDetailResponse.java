package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminShopDetailResponse(
    AdminShopResponse shop,

    @Schema(description = "Số sản phẩm của shop (mọi trạng thái)")
    long productCount,

    @Schema(description = "Số đơn hàng của shop (mọi trạng thái)")
    long orderCount,

    @Schema(description = "Lịch sử trạng thái, cũ nhất trước")
    List<StatusHistoryResponse> history
) {
}
