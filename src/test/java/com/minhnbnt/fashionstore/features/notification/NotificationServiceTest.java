package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.infrastructure.realtime.NotificationRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notifications;

    @Autowired
    private UserService users;

    @MockitoBean
    private NotificationRealtimePublisher publisher;

    @Test
    void createPersistsPerRecipientAndPushes() {
        var userA = user();
        var userB = user();

        notifications.create(
            Set.of(userA, userB),
            NotificationCategory.ORDER,
            "ORDER_PLACED",
            "Đơn mới",
            "Nội dung đơn hàng",
            "/account",
            null
        );

        assertThat(notifications.unreadCount(userA, null)).isEqualTo(1);
        assertThat(notifications.unreadCount(userB, null)).isEqualTo(1);

        var captorA = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(publisher).publish(eq(userA), captorA.capture());
        assertThat(captorA.getValue().category()).isEqualTo("ORDER");
        assertThat(captorA.getValue().type()).isEqualTo("ORDER_PLACED");

        var captorB = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(publisher).publish(eq(userB), captorB.capture());
        assertThat(captorB.getValue().category()).isEqualTo("ORDER");
        assertThat(captorB.getValue().type()).isEqualTo("ORDER_PLACED");
    }

    @Test
    void createIgnoresEmptyRecipients() {
        notifications.create(
            Set.of(),
            NotificationCategory.ORDER,
            "ORDER_PLACED",
            "Đơn mới",
            "Nội dung",
            "/account",
            null
        );

        verifyNoInteractions(publisher);
    }

    @Test
    void notifyInvitationUsesCommonPath() {
        var owner = user();
        var invitationId = UUID.randomUUID();
        notifications.notifyInvitation(owner, invitationId, "Shop A", "Nhân viên", "Chủ shop", "token-a");

        var page = notifications.page(owner, null, 0, 10);
        assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.category()).isEqualTo("ACTIVITY");
        assertThat(item.type()).isEqualTo("SHOP_INVITATION");
        assertThat(item.link()).isEqualTo("/invitations/token-a");

        var captor = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(publisher).publish(eq(owner), captor.capture());
        assertThat(captor.getValue().category()).isEqualTo("ACTIVITY");
    }

    @Test
    void deleteNotificationRequiresOwnership() {
        var owner = user();
        var stranger = user();
        notifications.create(Set.of(owner), NotificationCategory.ORDER, "ORDER_PLACED", "Đơn", "Body", null, null);
        var id = notifications.page(owner, null, 0, 10).items().getFirst().id();

        assertThatThrownBy(() -> notifications.deleteNotification(stranger, id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(notifications.page(owner, null, 0, 10).items()).hasSize(1);

        notifications.deleteNotification(owner, id);
        assertThat(notifications.page(owner, null, 0, 10).items()).isEmpty();
    }

    @Test
    void markReadOfAnotherUsersNotificationReturnsNotFound() {
        var owner = user();
        var stranger = user();
        notifications.notifyInvitation(owner, UUID.randomUUID(), "Shop A", "Nhân viên", "Chủ shop", "token-a");
        var notificationId = notifications.page(owner, null, 0, 10).items().getFirst().id();

        assertThatThrownBy(() -> notifications.markRead(stranger, notificationId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(notifications.unreadCount(owner, null)).isEqualTo(1);
    }

    @Test
    void pageClampsSizeAndReportsTotal() {
        var owner = user();
        notifications.notifyInvitation(owner, UUID.randomUUID(), "Shop A", "Nhân viên", "Chủ shop", "token-a");
        notifications.notifyInvitation(owner, UUID.randomUUID(), "Shop B", "Nhân viên", "Chủ shop", "token-b");

        var page = notifications.page(owner, null, -1, 500);

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
