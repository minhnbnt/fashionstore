package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Thống kê hiệu quả sản phẩm")
public record ProductStatsResponse(
    @Schema(description = "ID sản phẩm")
    UUID productId,

    @Schema(description = "Tên sản phẩm", example = "Áo Polo Nam Classic")
    String productName,

    @Schema(description = "S3 key của ảnh đại diện sản phẩm", nullable = true)
    @Nullable String imageKey,

    @Schema(description = "Tổng số lượng đã bán", example = "120")
    long totalSold,

    @Schema(description = "Tổng doanh thu mang lại (VND)", example = "24000000")
    long totalRevenue,

    @Schema(description = "Lượt xem sản phẩm", example = "3500")
    long viewCount,

    @Schema(description = "Tỷ lệ chuyển đổi mua hàng (orders / views)", example = "0.034")
    double conversionRate,

    @Schema(description = "Số lượng tồn kho hiện tại", example = "45")
    int currentStock
) {}
