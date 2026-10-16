package com.minhnbnt.fashionstore.dto.chat;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ChatConversationResponse(
    UUID id,
    UUID shopId,
    UUID buyerId,
    @Nullable String lastMessageText,
    Instant lastMessageAt,
    int unreadCount,
    Instant createdAt
) {
}
