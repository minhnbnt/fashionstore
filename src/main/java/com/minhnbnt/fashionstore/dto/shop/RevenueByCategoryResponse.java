package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Doanh thu phân theo danh mục sản phẩm")
public record RevenueByCategoryResponse(
    @Schema(description = "ID danh mục")
    UUID categoryId,

    @Schema(description = "Tên danh mục", example = "Áo Nam")
    String categoryName,

    @Schema(description = "Tổng doanh thu danh mục này (VND)", example = "15000000")
    long revenue,

    @Schema(description = "Tổng số lượng sản phẩm bán ra", example = "60")
    long quantity
) {}
