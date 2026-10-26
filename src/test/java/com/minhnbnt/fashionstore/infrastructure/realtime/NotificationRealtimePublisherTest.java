package com.minhnbnt.fashionstore.infrastructure.realtime;

import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messaging;

    @InjectMocks
    private NotificationRealtimePublisher publisher;

    @Test
    void publishSendsToUserQueue() {
        var recipientId = UUID.randomUUID();
        var notification = new NotificationResponse(
            UUID.randomUUID(),
            "ORDER",
            "ORDER_PLACED",
            "Có đơn hàng mới #101",
            "Khách hàng vừa đặt đơn #101 tại shop của bạn.",
            "/shop?tab=orders",
            null,
            Instant.now()
        );

        publisher.publish(recipientId, notification);

        verify(messaging).convertAndSendToUser(
            eq(recipientId.toString()),
            eq("/queue/notifications"),
            eq(notification)
        );
    }

    @Test
    void publishSwallowsMessagingFailure() {
        var recipientId = UUID.randomUUID();
        var notification = new NotificationResponse(
            UUID.randomUUID(),
            "ORDER",
            "ORDER_PLACED",
            "Có đơn hàng mới #101",
            "Khách hàng vừa đặt đơn #101 tại shop của bạn.",
            "/shop?tab=orders",
            null,
            Instant.now()
        );

        doThrow(new MessagingException("Broker unavailable"))
            .when(messaging).convertAndSendToUser(any(), any(), any());

        assertThatCode(() -> publisher.publish(recipientId, notification))
            .doesNotThrowAnyException();
    }
}
