package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Thông báo trong website. Lời mời nhân viên chỉ đi qua đây, không gửi email/SMTP.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String SHOP_INVITATION = "SHOP_INVITATION";

    private final NotificationRepository notifications;

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

        notifications.insert(userId, NotificationCategory.ACTIVITY, SHOP_INVITATION, title, body, "/invitations/" + token, invitationId);
    }

    public void deleteByRefId(UUID refId) {
        notifications.deleteByRefId(refId);
    }

    public PageResponse<NotificationResponse> page(UUID userId, int page, int size) {
        var targetPage = Math.max(0, page);
        var targetSize = Math.clamp(size, 1, 100);
        var total = notifications.countByUser(userId);
        var rows = notifications.findPage(userId, null, targetPage, targetSize);
        var items = rows.stream()
            .map(row -> new NotificationResponse(
                row.id(),
                row.category(),
                row.type(),
                row.title(),
                row.body(),
                row.link(),
                row.readAt(),
                row.createdAt()
            ))
            .toList();
        return new PageResponse<>(items, total, targetPage, targetSize);
    }

    public long unreadCount(UUID userId) {
        return notifications.countUnread(userId, null);
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
        notifications.markAllRead(userId, null);
    }
}
