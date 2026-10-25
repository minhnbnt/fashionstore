package com.minhnbnt.fashionstore.dto.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện "đã đọc" đẩy qua WebSocket khi một bên đọc hội thoại,
 * để bên còn lại hiển thị trạng thái đã xem cho tin nhắn của mình.
 */
public record ChatReadEvent(
    UUID conversationId,
    UUID readerId,
    Instant readAt
) {
}
