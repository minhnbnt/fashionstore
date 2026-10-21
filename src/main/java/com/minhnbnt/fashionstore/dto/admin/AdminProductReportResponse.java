package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AdminProductReportResponse(
    UUID id,

    @Schema(description = "USER (người mua) | SYSTEM (từ khóa cấm)", example = "USER")
    String source,

    @Schema(example = "COUNTERFEIT")
    String reason,

    @Schema(description = "Mô tả của người báo cáo; với SYSTEM là các từ khóa khớp, cách nhau bởi dấu phẩy")
    String description,

    @Schema(description = "Email người báo cáo, null với SYSTEM", nullable = true)
    @Nullable
    String reporterEmail,

    @Schema(description = "OPEN | ACTION_TAKEN | DISMISSED", example = "OPEN")
    String status,

    Instant createdAt,

    @Schema(nullable = true)
    @Nullable
    String resolvedByEmail,

    @Schema(nullable = true)
    @Nullable
    Instant resolvedAt,

    @Schema(description = "Lý do gỡ hoặc ghi chú khi bác bỏ", nullable = true)
    @Nullable
    String resolutionNote
) {
}
