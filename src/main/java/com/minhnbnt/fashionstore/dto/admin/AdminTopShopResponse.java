package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AdminTopShopResponse(
    @Schema(description = "Id shop")
    UUID shopId,

    @Schema(description = "Tên shop")
    String name,

    @Schema(description = "Slug trang gian hàng")
    String slug,

    @Schema(description = "Số đơn chưa hủy trong 30 ngày gần nhất")
    long orders,

    @Schema(description = "Tổng giá trị đơn chưa hủy trong 30 ngày gần nhất (VND)")
    long gmv
) {
}
