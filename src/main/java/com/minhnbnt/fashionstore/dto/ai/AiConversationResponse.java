package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AiConversationResponse(
    @Schema(description = "ID cuộc trò chuyện AI")
    UUID id,

    @Schema(description = "Tiêu đề cuộc trò chuyện")
    String title,

    @Schema(description = "Thời gian tạo")
    Instant createdAt,

    @Schema(description = "Thời gian cập nhật gần nhất")
    Instant updatedAt
) {
}
