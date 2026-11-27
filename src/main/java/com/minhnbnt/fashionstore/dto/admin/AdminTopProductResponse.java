package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AdminTopProductResponse(
    @Schema(description = "Id sản phẩm")
    UUID productId,

    @Schema(description = "Tên sản phẩm")
    String name,

    @Schema(description = "Tên shop bán")
    String shopName,

    @Schema(description = "Số lượng bán trong đơn chưa hủy, 30 ngày gần nhất")
    long quantity,

    @Schema(description = "Doanh số dòng hàng (đơn giá x số lượng), 30 ngày gần nhất (VND)")
    long revenue
) {
}
