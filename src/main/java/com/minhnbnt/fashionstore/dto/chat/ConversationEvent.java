package com.minhnbnt.fashionstore.dto.chat;

import java.util.UUID;

/**
 * Sự kiện tổng hợp đẩy qua WebSocket để cập nhật danh sách hội thoại
 * (preview tin nhắn, badge chưa đọc) mà không cần subscribe từng hội thoại.
 */
public record ConversationEvent(
    UUID conversationId,
    Type type
) {

    public enum Type {
        NEW_MESSAGE
    }
}
