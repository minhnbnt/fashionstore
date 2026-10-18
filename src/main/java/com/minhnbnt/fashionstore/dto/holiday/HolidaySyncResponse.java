package com.minhnbnt.fashionstore.dto.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record HolidaySyncResponse(
    @Schema(description = "SUCCESS | FAILED", example = "SUCCESS")
    String status,

    @Schema(description = "Lý do thất bại, rỗng nếu thành công")
    String message,

    @Schema(description = "Số ngày Calendarific trả về sau khi lọc")
    int holidayCount,

    Instant syncedAt
) {
}
