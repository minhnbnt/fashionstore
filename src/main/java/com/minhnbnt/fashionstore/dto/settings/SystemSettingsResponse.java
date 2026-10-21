package com.minhnbnt.fashionstore.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;

public record SystemSettingsResponse(
    @Schema(description = "Tên sàn", example = "nét")
    String siteName,

    @Schema(description = "Hotline hiển thị", example = "1900 0000")
    String hotline,

    @Schema(description = "Email hỗ trợ", example = "support@fashionstore.local")
    String supportEmail,

    @Schema(description = "Ngưỡng miễn phí vận chuyển, VND", example = "500000")
    long freeShippingThreshold,

    @Schema(description = "Số sản phẩm tối đa tính theo phí cơ bản", example = "2")
    int baseItems,

    @Schema(description = "Số phút giữ đơn chờ thanh toán online", example = "15")
    int paymentHoldMinutes,

    @Schema(description = "Số ngày tự hoàn tất đơn sau khi giao", example = "7")
    int autoCompleteDays,

    @Schema(description = "Tổng đơn tối thiểu cho COD, VND", example = "0")
    long codMinTotal,

    @Schema(description = "Tổng đơn tối đa cho COD, VND; 0 = không giới hạn", example = "10000000")
    long codMaxTotal
) {
}
