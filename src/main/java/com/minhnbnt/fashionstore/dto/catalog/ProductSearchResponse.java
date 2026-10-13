package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ProductSearchResponse(
    @Schema List<ProductSummaryResponse> items,
    @Schema(description = "Tổng số sản phẩm khớp bộ lọc, không tính phân trang") Long total,
    @Schema(description = "Trang hiện tại, đếm từ 0") Integer page,
    @Schema(description = "Số sản phẩm mỗi trang") Integer limit
) {
}
