package com.minhnbnt.fashionstore.infrastructure.realtime;

import com.minhnbnt.fashionstore.dto.chat.ChatMessageResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatReadEvent;
import com.minhnbnt.fashionstore.dto.chat.ConversationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messaging;

    @InjectMocks
    private ChatRealtimePublisher publisher;

    @Test
    void publishChatMessageSendsToEachRecipientUserQueue() {
        var conversationId = UUID.randomUUID();
        var buyerId = UUID.randomUUID();
        var sellerId = UUID.randomUUID();
        var message = new ChatMessageResponse(
            UUID.randomUUID(), conversationId, buyerId,
            "BUYER", "Hello", null, Instant.now(), false
        );

        publisher.publishChatMessage(Set.of(buyerId, sellerId), message);

        verify(messaging).convertAndSendToUser(buyerId.toString(), "/queue/chat/" + conversationId, message);
        verify(messaging).convertAndSendToUser(sellerId.toString(), "/queue/chat/" + conversationId, message);
    }

    @Test
    void publishChatMessageSwallowsFailureToProtectRestFlow() {
        var conversationId = UUID.randomUUID();
        var buyerId = UUID.randomUUID();
        var sellerId = UUID.randomUUID();
        var message = new ChatMessageResponse(
            UUID.randomUUID(), conversationId, buyerId,
            "BUYER", "Hello", null, Instant.now(), false
        );
        doThrow(new RuntimeException("broker down"))
            .when(messaging)
            .convertAndSendToUser(eq(buyerId.toString()), anyString(), any());

        assertThatCode(() -> publisher.publishChatMessage(Set.of(buyerId, sellerId), message))
            .doesNotThrowAnyException();

        verify(messaging).convertAndSendToUser(sellerId.toString(), "/queue/chat/" + conversationId, message);
    }

    @Test
    void publishReadReceiptSendsToConversationReadQueue() {
        var conversationId = UUID.randomUUID();
        var event = new ChatReadEvent(conversationId, UUID.randomUUID(), Instant.now());

        publisher.publishReadReceipt(Set.of(UUID.randomUUID()), event);

        verify(messaging).convertAndSendToUser(
            anyString(), eq("/queue/chat/" + conversationId + "/read"), eq(event)
        );
    }

    @Test
    void publishConversationEventSendsToConversationsQueue() {
        var event = new ConversationEvent(UUID.randomUUID(), ConversationEvent.Type.NEW_MESSAGE);

        publisher.publishConversationEvent(Set.of(UUID.randomUUID()), event);

        verify(messaging).convertAndSendToUser(anyString(), eq("/queue/conversations"), eq(event));
    }
}
