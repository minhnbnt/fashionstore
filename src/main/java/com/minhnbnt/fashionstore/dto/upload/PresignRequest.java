package com.minhnbnt.fashionstore.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

public record PresignRequest(
    @Schema(description = "Chỉ nhận image/* hoặc video/*", example = "image/jpeg")
    @NotBlank
    @Pattern(regexp = "^(image|video)/[a-zA-Z0-9.+-]+$")
    String contentType,

    @Schema(description = "Mục đích upload: MEDIA (mặc định) hoặc KYC cho giấy tờ")
    @Nullable
    UploadPurpose purpose
) {
}
