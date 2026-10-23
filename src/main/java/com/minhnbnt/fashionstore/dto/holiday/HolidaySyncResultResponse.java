package com.minhnbnt.fashionstore.dto.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

public record HolidaySyncResultResponse(
    int year,

    @Schema(description = "Ngày mới thêm")
    int added,

    @Schema(description = "Ngày seed/đồng bộ trước đó được cập nhật")
    int updated,

    @Schema(description = "Ngày đồng bộ trước đó mà Calendarific đã rút lại")
    int removed,

    @Schema(description = "Ngày admin đã sửa tay nên giữ nguyên")
    int keptManual
) {
}
