package com.minhnbnt.fashionstore.dto.chat;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderId,
    String senderType,
    String content,
    @Nullable String imageKey,
    Instant sentAt,
    boolean isRead
) {
}
