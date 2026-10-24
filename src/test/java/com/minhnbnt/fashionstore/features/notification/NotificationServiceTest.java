package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notifications;

    @Autowired
    private UserService users;

    @Test
    void markReadOfAnotherUsersNotificationReturnsNotFound() {
        var owner = user();
        var stranger = user();
        notifications.notifyInvitation(owner, UUID.randomUUID(), "Shop A", "Nhân viên", "Chủ shop", "token-a");
        var notificationId = notifications.page(owner, 0, 10).items().getFirst().id();

        assertThatThrownBy(() -> notifications.markRead(stranger, notificationId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(notifications.unreadCount(owner)).isEqualTo(1);
    }

    @Test
    void pageClampsSizeAndReportsTotal() {
        var owner = user();
        notifications.notifyInvitation(owner, UUID.randomUUID(), "Shop A", "Nhân viên", "Chủ shop", "token-a");
        notifications.notifyInvitation(owner, UUID.randomUUID(), "Shop B", "Nhân viên", "Chủ shop", "token-b");

        var page = notifications.page(owner, -1, 500);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
    }

    private UUID user() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("notification-service-" + id + "@t.local", "Notification User", null));
        return id;
    }
}
