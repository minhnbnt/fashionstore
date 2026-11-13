package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public record SaveAiMessageRequest(
    @Schema(description = "Vai trò: user, assistant, system")
    @NotBlank
    String role,

    @Schema(description = "Nội dung văn bản tin nhắn")
    String content,

    @Schema(description = "Metadata JSON (mảng parts của AI SDK)", nullable = true)
    @Nullable
    String metadata
) {
}
