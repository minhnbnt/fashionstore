package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Danh mục sản phẩm của shop kèm số lượng sản phẩm")
public record ShopCategorySummaryResponse(
    @Schema(description = "Id danh mục") UUID id,
    @Schema(description = "Tên danh mục") String name,
    @Schema(description = "Slug danh mục") String slug,
    @Schema(description = "Số lượng sản phẩm của shop trong danh mục") long productCount
) {}
