package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record SaveAiMessageRequest(
    @Schema(description = "Vai trò: user, assistant, system")
    @NotBlank
    @Pattern(regexp = "user|assistant|system", message = "role must be user, assistant or system")
    String role,

    @Schema(description = "Nội dung văn bản tin nhắn", nullable = true)
    @Nullable
    @Size(max = 10000, message = "content must be at most 10000 characters")
    String content,

    @Schema(description = "Metadata JSON (mảng parts của AI SDK)", nullable = true)
    @Nullable
    @Size(max = 100000, message = "metadata must be at most 100000 characters")
    String metadata
) {
}
