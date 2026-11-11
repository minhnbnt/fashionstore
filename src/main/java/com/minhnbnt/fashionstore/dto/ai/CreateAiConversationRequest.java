package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record CreateAiConversationRequest(
    @Schema(description = "Tiêu đề cuộc trò chuyện AI", nullable = true)
    @Nullable
    String title
) {
}
