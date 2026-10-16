package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public record SendMessageRequest(
    @Schema(description = "Nội dung tin nhắn")
    @NotBlank
    String content,

    @Schema(description = "S3 key ảnh đính kèm", nullable = true)
    @Nullable
    String imageKey
) {
}
