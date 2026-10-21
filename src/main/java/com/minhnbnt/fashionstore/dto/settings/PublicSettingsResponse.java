package com.minhnbnt.fashionstore.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;

public record PublicSettingsResponse(
    @Schema(description = "Tên sàn", example = "nét")
    String siteName,

    @Schema(description = "Hotline hiển thị", example = "1900 0000")
    String hotline,

    @Schema(description = "Email hỗ trợ", example = "support@fashionstore.local")
    String supportEmail,

    @Schema(description = "Ngưỡng miễn phí vận chuyển, VND", example = "500000")
    long freeShippingThreshold,

    @Schema(description = "Số sản phẩm tối đa tính theo phí cơ bản", example = "2")
    int baseItems
) {
}
