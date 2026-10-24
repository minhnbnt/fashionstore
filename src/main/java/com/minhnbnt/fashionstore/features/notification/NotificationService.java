package com.minhnbnt.fashionstore.features.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        notifications.insert(userId, SHOP_INVITATION, title, body, "/invitations/" + token, invitationId);
    }

    public void deleteByRefId(UUID refId) {
        notifications.deleteByRefId(refId);
    }
}
