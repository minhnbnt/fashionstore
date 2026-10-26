package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
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
    void insertReturnsRowWithCategory() {
        var userId = user();
        var row = notifications.insert(
            userId, NotificationCategory.ORDER, "ORDER_PLACED", "Đơn mới", "Chi tiết", "/account", null
        );

        assertThat(row.id()).isNotNull();
        assertThat(row.category()).isEqualTo("ORDER");
        assertThat(row.type()).isEqualTo("ORDER_PLACED");
        assertThat(row.createdAt()).isNotNull();
    }

    @Test
    void findPageFiltersByCategories() {
        var userId = user();
        notifications.insert(userId, NotificationCategory.ORDER, "ORDER_PLACED", "Đơn hàng", "Body", null, null);
        notifications.insert(userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Hoạt động", "Body", null, null);

        var orderItems = notifications.findPage(userId, List.of("ORDER"), 0, 20);
        assertThat(orderItems).hasSize(1);
        assertThat(orderItems.getFirst().type()).isEqualTo("ORDER_PLACED");
        assertThat(orderItems.getFirst().category()).isEqualTo("ORDER");

        var allItems = notifications.findPage(userId, null, 0, 20);
        assertThat(allItems).hasSize(2);
    }

    @Test
    void unreadCountFiltersByCategories() {
        var userId = user();
        notifications.insert(userId, NotificationCategory.ORDER, "ORDER_PLACED", "Đơn 1", "Body", null, null);
        notifications.insert(
            userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Hoạt động 1", "Body", null, null
        );

        assertThat(notifications.countUnread(userId, List.of("ACTIVITY"))).isEqualTo(1);
        assertThat(notifications.countUnread(userId, null)).isEqualTo(2);
    }

    @Test
    void countUnreadByCategoryGroupsCorrectly() {
        var userId = user();
        notifications.insert(userId, NotificationCategory.ORDER, "ORDER_PLACED", "Đơn 1", "Body", null, null);
        notifications.insert(
            userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Hoạt động 1", "Body", null, null
        );
        notifications.insert(
            userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Hoạt động 2", "Body", null, null
        );

        var counts = notifications.countUnreadByCategory(userId, null);
        assertThat(counts).containsEntry("ORDER", 1L);
        assertThat(counts).containsEntry("ACTIVITY", 2L);

        var filtered = notifications.countUnreadByCategory(userId, List.of("ORDER"));
        assertThat(filtered).containsEntry("ORDER", 1L);
        assertThat(filtered).doesNotContainKey("ACTIVITY");
    }

    @Test
    void markAllReadScopedByCategories() {
        var userId = user();
        notifications.insert(userId, NotificationCategory.ORDER, "ORDER_PLACED", "Đơn 1", "Body", null, null);
        notifications.insert(
            userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Hoạt động 1", "Body", null, null
        );

        notifications.markAllRead(userId, List.of("ORDER"));

        assertThat(notifications.countUnread(userId, List.of("ACTIVITY"))).isEqualTo(1);
        assertThat(notifications.countUnread(userId, List.of("ORDER"))).isZero();
    }

    @Test
    void deleteRemovesOwnNotificationOnly() {
        var owner = user();
        var stranger = user();
        var row = notifications.insert(
            owner, NotificationCategory.ORDER, "ORDER_PLACED", "Đơn hàng", "Body", null, null
        );

        notifications.delete(stranger, row.id());
        assertThat(notifications.findPage(owner, null, 0, 10)).hasSize(1);

        notifications.delete(owner, row.id());
        assertThat(notifications.findPage(owner, null, 0, 10)).isEmpty();
    }

    @Test
    void deleteByRefIdRemovesOnlyMatchingRows() {
        var userId = user();
        var refId = UUID.randomUUID();

        notifications.insert(userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Có ref", "Body", null, refId);
        notifications.insert(userId, NotificationCategory.ACTIVITY, "SHOP_INVITATION", "Không ref", "Body", null, null);

        notifications.deleteByRefId(refId);

        assertThat(notifications.findPage(userId, null, 0, 10))
            .extracting(NotificationRepository.NotificationRow::title)
            .containsExactly("Không ref");
    }

    private UUID user() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("notification-" + id + "@t.local", "Notification User", null));
        return id;
    }
}
