package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderId,
    String senderType,
    String content,
    @Schema(description = "URL public ảnh đính kèm, null = tin nhắn chỉ có chữ")
    @Nullable String imageUrl,
    Instant sentAt,
    boolean isRead
) {
}
