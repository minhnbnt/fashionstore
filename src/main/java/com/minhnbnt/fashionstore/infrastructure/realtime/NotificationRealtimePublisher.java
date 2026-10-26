package com.minhnbnt.fashionstore.infrastructure.realtime;

import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Biên hạ tầng realtime của thông báo — nơi duy nhất biết về STOMP và cách đặt tên destination /queue/notifications.
 * Lỗi gửi được nuốt lại (chỉ log warn) để luồng nghiệp vụ qua REST không bao giờ bị ảnh hưởng:
 * HTTP là nguồn chân lý, WebSocket chỉ là lớp phủ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRealtimePublisher {

    private final SimpMessagingTemplate messaging;

    public void publish(UUID recipientId, NotificationResponse notification) {
        try {
            messaging.convertAndSendToUser(recipientId.toString(), "/queue/notifications", notification);
        } catch (Exception e) {
            log.warn("Failed to push realtime notification to user {}: {}", recipientId, e.getMessage());
        }
    }
}
