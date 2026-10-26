package com.minhnbnt.fashionstore.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateSettingsRequest(
    @Schema(description = "Tên sàn, bỏ trống = không đổi", example = "nét")
    @Size(min = 1, max = 100)
    @Nullable String siteName,

    @Schema(description = "Hotline hiển thị, bỏ trống = không đổi", example = "1900 0000")
    @Size(max = 20)
    @Nullable String hotline,

    @Schema(description = "Email hỗ trợ, bỏ trống = không đổi", example = "support@fashionstore.local")
    @Email
    @Nullable String supportEmail,

    @Schema(description = "Ngưỡng miễn phí vận chuyển, VND, bỏ trống = không đổi", example = "500000")
    @Min(0)
    @Nullable Long freeShippingThreshold,

    @Schema(description = "Số sản phẩm tối đa tính theo phí cơ bản (1-20), bỏ trống = không đổi", example = "2")
    @Min(1) @Max(20)
    @Nullable Integer baseItems,

    @Schema(description = "Số phút giữ đơn chờ thanh toán online (5-1440), bỏ trống = không đổi", example = "15")
    @Min(5) @Max(1440)
    @Nullable Integer paymentHoldMinutes,

    @Schema(description = "Số ngày tự hoàn tất đơn sau khi giao (1-30), bỏ trống = không đổi", example = "7")
    @Min(1) @Max(30)
    @Nullable Integer autoCompleteDays,

    @Schema(description = "Tổng đơn tối thiểu cho COD, VND, bỏ trống = không đổi", example = "0")
    @Min(0)
    @Nullable Long codMinTotal,

    @Schema(
        description = "Tổng đơn tối đa cho COD, VND; 0 = không giới hạn; bỏ trống = không đổi",
        example = "10000000"
    )
    @Min(0)
    @Nullable Long codMaxTotal
) {
}
