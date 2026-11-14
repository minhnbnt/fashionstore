package com.minhnbnt.fashionstore.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PresignResponse(
    @Schema(description = "Id bản ghi upload — gửi vào các request nghiệp vụ thay cho key") UUID uploadId,
    @Schema(description = "URL PUT file trực tiếp lên S3") String uploadUrl,
    @Schema(description = "URL public của object sau khi PUT xong — dùng cho nội dung FE tự quản (decoration draft)") String publicUrl,
    @Schema Instant expiresAt
) {
}
