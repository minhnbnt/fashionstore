package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.notification.NotificationCountResponse;
import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import com.minhnbnt.fashionstore.infrastructure.realtime.NotificationRealtimePublisher;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thông báo trong website — mọi loại thông báo đi qua create(), realtime push qua NotificationRealtimePublisher.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String SHOP_INVITATION = "SHOP_INVITATION";

    private final NotificationRepository notifications;
    private final NotificationRealtimePublisher publisher;

    @Transactional
    public void create(
        Collection<UUID> recipients,
        NotificationCategory category,
        String type,
        String title,
        String body,
        @Nullable String link,
        @Nullable UUID refId
    ) {
        if (recipients.isEmpty()) {
            return;
        }

        for (var recipientId : recipients) {
            var row = notifications.insert(recipientId, category, type, title, body, link, refId);
            publisher.publish(recipientId, toResponse(row));
        }
    }

    @Transactional
    public void notifyInvitation(
        UUID userId,
        UUID invitationId,
        String shopName,
        String roleName,
        String ownerName,
        String token
    ) {
        var title = "Lời mời tham gia shop " + shopName;
        var body = ownerName + " mời bạn làm " + roleName + " tại " + shopName + ".";

        create(Set.of(userId), NotificationCategory.ACTIVITY, SHOP_INVITATION, title, body, "/invitations/" + token, invitationId);
    }

    public void deleteByRefId(UUID refId) {
        notifications.deleteByRefId(refId);
    }

    public PageResponse<NotificationResponse> page(UUID userId, int page, int size) {
        return page(userId, null, page, size);
    }

    public PageResponse<NotificationResponse> page(
        UUID userId,
        @Nullable List<String> categories,
        int page,
        int size
    ) {
        var targetPage = Math.max(0, page);
        var targetSize = Math.clamp(size, 1, 100);
        var total = notifications.countByUser(userId);
        var rows = notifications.findPage(userId, categories, targetPage, targetSize);
        var items = rows.stream()
            .map(this::toResponse)
            .toList();
        return new PageResponse<>(items, total, targetPage, targetSize);
    }

    public long unreadCount(UUID userId) {
        return unreadCount(userId, null);
    }

    public long unreadCount(UUID userId, @Nullable List<String> categories) {
        return notifications.countUnread(userId, categories);
    }

    public NotificationCountResponse unreadSummary(UUID userId) {
        return unreadSummary(userId, null);
    }

    public NotificationCountResponse unreadSummary(UUID userId, @Nullable List<String> categories) {
        var unread = notifications.countUnread(userId, categories);
        var byCategory = notifications.countUnreadByCategory(userId, categories);
        return new NotificationCountResponse(unread, byCategory);
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        if (!notifications.exists(userId, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        notifications.markRead(userId, id);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        markAllRead(userId, null);
    }

    @Transactional
    public void markAllRead(UUID userId, @Nullable List<String> categories) {
        notifications.markAllRead(userId, categories);
    }

    @Transactional
    public void deleteNotification(UUID userId, UUID id) {
        if (!notifications.exists(userId, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        notifications.delete(userId, id);
    }

    private NotificationResponse toResponse(NotificationRepository.NotificationRow row) {
        return new NotificationResponse(
            row.id(),
            row.category(),
            row.type(),
            row.title(),
            row.body(),
            row.link(),
            row.readAt(),
            row.createdAt()
        );
    }
}
