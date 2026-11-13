package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateAiConversationRequest(
    @Schema(description = "Tiêu đề cuộc trò chuyện AI", nullable = true)
    @Nullable
    @Size(max = 255, message = "title must be at most 255 characters")
    String title
) {
}
