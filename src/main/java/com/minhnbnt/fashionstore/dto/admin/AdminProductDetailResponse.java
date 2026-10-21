package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminProductDetailResponse(
    AdminProductResponse product,

    String description,

    List<VariantResponse> variants,

    List<ImageResponse> images,

    @Schema(description = "Lịch sử kiểm duyệt, cũ nhất trước")
    List<ProductModerationHistoryResponse> history,

    @Schema(description = "Báo cáo của sản phẩm (mọi trạng thái), mới nhất trước, tối đa 100")
    List<AdminProductReportResponse> reports
) {
}
