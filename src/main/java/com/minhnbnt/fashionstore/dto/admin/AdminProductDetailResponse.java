package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record AdminProductDetailResponse(
    AdminProductResponse product,

    String description,

    List<VariantResponse> variants,

    List<ImageResponse> images,

    @Schema(description = "Lịch sử kiểm duyệt, cũ nhất trước")
    List<ProductModerationHistoryResponse> history,

    @Schema(description = "Báo cáo của sản phẩm (mọi trạng thái), mới nhất trước, tối đa 100")
    List<AdminProductReportResponse> reports,

    @Schema(nullable = true) @Nullable String videoKey,
    @Schema(nullable = true) @Nullable String garmentKey,
    @Schema(description = "Hỗ trợ thử đồ ảo") Boolean tryOnEnabled,
    @Schema(nullable = true) @Nullable String material,
    @Schema(nullable = true) @Nullable String origin,
    @Schema(nullable = true) @Nullable String careInstructions,
    @Schema(nullable = true) @Nullable String sizeGuide,
    @Schema(nullable = true) @Nullable Integer weightGrams,
    @Schema(nullable = true) @Nullable Integer lengthCm,
    @Schema(nullable = true) @Nullable Integer widthCm,
    @Schema(nullable = true) @Nullable Integer heightCm
) {
    public AdminProductDetailResponse(
        AdminProductResponse product,
        String description,
        List<VariantResponse> variants,
        List<ImageResponse> images,
        List<ProductModerationHistoryResponse> history,
        List<AdminProductReportResponse> reports
    ) {
        this(
            product, description, variants, images, history, reports,
            null, null, false, null, null, null, null, null, null, null, null
        );
    }
}
