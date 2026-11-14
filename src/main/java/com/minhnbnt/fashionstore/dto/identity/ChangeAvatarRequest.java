package com.minhnbnt.fashionstore.dto.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeAvatarRequest(
    @Schema(description = "Id upload ảnh đã PUT lên S3 qua /api/uploads/presign")
    @NotNull
    UUID avatarUploadId
) {
}
