package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ghim SQL của NotificationRepository: roundtrip insert, phân trang, đếm chưa đọc,
 * đánh dấu đã đọc (chỉ của chính user) và xóa theo ref_id.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationRepositoryIntegrationTest {

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private UserService users;

    @Test
    void insertListCountAndMarkRead() {
        var userId = user();

        notifications.insert(userId, "SHOP_INVITATION", "Lời mời", "Tham gia shop", "/invitations/abc", null);
        notifications.insert(userId, "SHOP_INVITATION", "Lời mời 2", "Tham gia shop 2", null, null);

        assertThat(notifications.countUnread(userId)).isEqualTo(2);

        var page = notifications.findPage(userId, 0, 1);
        assertThat(page).hasSize(1);
        assertThat(notifications.findPage(userId, 1, 1)).hasSize(1);

        var first = page.getFirst();
        assertThat(first.type()).isEqualTo("SHOP_INVITATION");
        assertThat(first.readAt()).isNull();

        notifications.markRead(userId, first.id());
        assertThat(notifications.countUnread(userId)).isEqualTo(1);

        notifications.markRead(user(), first.id());
        assertThat(notifications.countUnread(userId)).isEqualTo(1);

        notifications.markAllRead(userId);
        assertThat(notifications.countUnread(userId)).isZero();
    }

    @Test
    void deleteByRefIdRemovesOnlyMatchingRows() {
        var userId = user();
        var refId = UUID.randomUUID();

        notifications.insert(userId, "SHOP_INVITATION", "Có ref", "Body", null, refId);
        notifications.insert(userId, "SHOP_INVITATION", "Không ref", "Body", null, null);

        notifications.deleteByRefId(refId);

        assertThat(notifications.findPage(userId, 0, 10))
            .extracting(NotificationRepository.NotificationRow::title)
            .containsExactly("Không ref");
    }

    private UUID user() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("notification-" + id + "@t.local", "Notification User", null));
        return id;
    }
}
