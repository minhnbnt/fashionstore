package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Tùy chọn sản phẩm trong bộ lọc đánh giá của shop")
public record SellerReviewProductOptionResponse(
    @Schema(description = "ID sản phẩm") UUID id,
    @Schema(description = "Tên sản phẩm") String name,
    @Schema(description = "Tổng số đánh giá của sản phẩm") long reviewCount
) {
}

