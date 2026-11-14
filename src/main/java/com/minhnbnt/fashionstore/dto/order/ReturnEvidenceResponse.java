package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReturnEvidenceResponse(
    @Schema(description = "URL public truy cập file bằng chứng") String url
) {
}
