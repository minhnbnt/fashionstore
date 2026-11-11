package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateAiConversationRequest(
    @Schema(description = "Tiêu đề cuộc trò chuyện AI")
    @NotBlank
    String title
) {
}
