package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ProductReportResponse(
    UUID id,

    @Schema(example = "COUNTERFEIT")
    String reason,

    @Schema(description = "OPEN | ACTION_TAKEN | DISMISSED", example = "OPEN")
    String status,

    Instant createdAt
) {
}
