package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record AdminDailyStatResponse(
    @Schema(description = "Ngày (giờ Việt Nam)", example = "2026-10-16")
    LocalDate date,

    @Schema(description = "Tài khoản mới tạo trong ngày")
    long newUsers,

    @Schema(description = "Shop mới đăng ký trong ngày")
    long newShops,

    @Schema(description = "Đơn đặt trong ngày (kể cả đơn đã hủy)")
    long orders,

    @Schema(description = "Tổng giá trị đơn chưa hủy đặt trong ngày (VND)")
    long orderValue
) {
}
