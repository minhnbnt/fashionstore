package com.minhnbnt.fashionstore.infrastructure.realtime;

import com.minhnbnt.fashionstore.dto.chat.ChatMessageResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatReadEvent;
import com.minhnbnt.fashionstore.dto.chat.ConversationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Biên hạ tầng realtime của chat — nơi duy nhất biết về STOMP và cách đặt tên destination.
 * Lời gửi lỗi được nuốt lại (chỉ log warn) để luồng gửi tin nhắn qua REST không bao giờ
 * bị ảnh hưởng: HTTP là nguồn chân lý, WebSocket chỉ là lớp phủ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRealtimePublisher {

    private final SimpMessagingTemplate messaging;

    public void publishChatMessage(Set<UUID> recipientIds, ChatMessageResponse message) {
        var destination = "/queue/chat/" + message.conversationId();
        for (var recipientId : recipientIds) {
            sendQuietly(recipientId, destination, message);
        }
    }

    public void publishReadReceipt(Set<UUID> recipientIds, ChatReadEvent event) {
        var destination = "/queue/chat/" + event.conversationId() + "/read";
        for (var recipientId : recipientIds) {
            sendQuietly(recipientId, destination, event);
        }
    }

    public void publishConversationEvent(Set<UUID> recipientIds, ConversationEvent event) {
        for (var recipientId : recipientIds) {
            sendQuietly(recipientId, "/queue/conversations", event);
        }
    }

    private void sendQuietly(UUID recipientId, String destination, Object payload) {
        try {
            messaging.convertAndSendToUser(recipientId.toString(), destination, payload);
        } catch (Exception e) {
            log.warn("Failed to push realtime chat to user {}: {}", recipientId, e.getMessage());
        }
    }
}
