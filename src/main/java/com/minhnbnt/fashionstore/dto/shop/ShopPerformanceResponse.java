package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Hiệu suất vận hành của Shop")
public record ShopPerformanceResponse(
    @Schema(description = "Tỷ lệ phản hồi chat (0.0 - 1.0)", example = "0.95")
    double chatResponseRate,

    @Schema(description = "Tỷ lệ hủy đơn (0.0 - 1.0)", example = "0.02")
    double cancelRate,

    @Schema(description = "Tỷ lệ giao hàng đúng hạn (0.0 - 1.0)", example = "0.98")
    double onTimeDeliveryRate,

    @Schema(description = "Điểm đánh giá trung bình (1.0 - 5.0)", example = "4.9")
    double avgRating
) {}
