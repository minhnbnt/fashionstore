package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void listRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOwnNotifications() throws Exception {
        var userId = UUID.randomUUID();
        var createdAt = Instant.parse("2026-10-24T10:00:00Z");
        var notification = new NotificationResponse(
            UUID.randomUUID(),
            "SHOP_INVITATION",
            "Lời mời tham gia shop An Nhiên",
            "Chủ shop mời bạn làm nhân viên.",
            "/invitations/token-1",
            null,
            createdAt
        );
        when(notificationService.page(userId, 0, 20))
            .thenReturn(new PageResponse<>(List.of(notification), 1, 0, 20));

        mockMvc.perform(get("/api/notifications")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("Lời mời tham gia shop An Nhiên"))
            .andExpect(jsonPath("$.items[0].link").value("/invitations/token-1"))
            .andExpect(jsonPath("$.total").value(1));

        verify(notificationService).page(userId, 0, 20);
    }

    @Test
    void unreadCountReturnsNumber() throws Exception {
        var userId = UUID.randomUUID();
        when(notificationService.unreadCount(userId)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(3));

        verify(notificationService).unreadCount(userId);
    }

    @Test
    void markReadRequiresOwnNotification() throws Exception {
        var userId = UUID.randomUUID();
        var notificationId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"))
            .when(notificationService).markRead(userId, notificationId);

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void markAllReturns204() throws Exception {
        var userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/read-all")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(userId);
    }
}
