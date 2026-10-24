package com.minhnbnt.fashionstore.dto.holiday;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record HolidayYearResponse(
    int year,

    @Schema(description = "Đã cấu hình API key Calendarific chưa, false thì không đồng bộ được")
    boolean syncConfigured,

    @Schema(description = "Lần đồng bộ gần nhất của năm, null nếu chưa từng", nullable = true)
    @Nullable
    HolidaySyncResponse lastSync,

    @Schema(description = "Mọi ngày của năm kể cả ngày đã bỏ, sớm nhất trước")
    List<HolidayResponse> holidays
) {
}
