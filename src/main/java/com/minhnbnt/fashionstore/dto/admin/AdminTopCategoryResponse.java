package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminTopCategoryResponse(
    @Schema(description = "Tên danh mục; sản phẩm chưa gắn danh mục gom vào \"Chưa phân loại\"")
    String name,

    @Schema(description = "Số lượng bán trong đơn chưa hủy, 30 ngày gần nhất")
    long quantity,

    @Schema(description = "Doanh số dòng hàng trong 30 ngày gần nhất (VND)")
    long revenue
) {
}
