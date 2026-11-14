package com.minhnbnt.fashionstore.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PresignResponse(
    @Schema(description = "Id bản ghi upload — gửi vào các request nghiệp vụ thay cho key") UUID uploadId,
    @Schema(description = "URL PUT file trực tiếp lên S3") String uploadUrl,
    @Schema Instant expiresAt
) {
}
