package com.minhnbnt.fashionstore.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AiMessageResponse(
    @Schema(description = "ID tin nhắn")
    UUID id,

    @Schema(description = "ID cuộc trò chuyện")
    UUID conversationId,

    @Schema(description = "Vai trò người gửi: user, assistant, system")
    String role,

    @Schema(description = "Nội dung tin nhắn")
    String content,

    @Schema(description = "Metadata JSON (chứa mảng parts của AI SDK và tool outputs)", nullable = true)
    @Nullable
    String metadata,

    @Schema(description = "Thời gian gửi")
    Instant sentAt
) {
}
