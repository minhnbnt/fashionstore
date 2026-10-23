package com.minhnbnt.fashionstore.dto.holiday;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

public record HolidayResponse(
    @Schema(description = "Ngày (giờ Việt Nam)", example = "2026-11-24")
    LocalDate day,

    @Schema(example = "Ngày Văn hóa Việt Nam")
    String name,

    @Schema(description = "HOLIDAY (nghỉ) | WORKING_DAY (đi làm bù)", example = "HOLIDAY")
    String kind,

    @Schema(description = "SEED | CALENDARIFIC | MANUAL", example = "CALENDARIFIC")
    String source,

    @Schema(description = "false = admin đã bỏ ngày này, đồng bộ không thêm lại")
    boolean enabled,

    @Schema(description = "Email admin sửa gần nhất, null nếu do seed/đồng bộ", nullable = true)
    @Nullable
    String updatedByEmail,

    Instant updatedAt
) {
}
