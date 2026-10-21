package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record DismissReportsResponse(
    @Schema(description = "Số báo cáo đã đóng")
    int dismissedCount
) {
}
