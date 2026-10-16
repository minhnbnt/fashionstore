package com.minhnbnt.fashionstore.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Gọi GoTrue Admin API (PUT /admin/users/{id}) bằng service role key.
 * Key trống (dev chưa cấu hình) thì bỏ qua + log cảnh báo: backend vẫn chặn tài khoản khóa qua AccountFilter,
 * chỉ mất lớp chặn đăng nhập/refresh token ở GoTrue.
 */
@Component
@Slf4j
public class GoTrueAdminClient {

    /**
     * GoTrue không có cờ "ban vĩnh viễn", dùng 100 năm như Supabase dashboard.
     */
    private static final String PERMANENT_BAN = "876000h";

    private final RestClient rest;

    private final String serviceRoleKey;

    @Autowired
    public GoTrueAdminClient(
        @Value("${gotrue.url:http://localhost:9999}") String url,
        @Value("${gotrue.service-role-key:}") String serviceRoleKey
    ) {
        this(RestClient.builder(), url, serviceRoleKey);
    }

    GoTrueAdminClient(RestClient.Builder builder, String url, String serviceRoleKey) {
        this.rest = builder.baseUrl(url).build();
        this.serviceRoleKey = serviceRoleKey;
    }

    public void banUser(UUID userId) {
        updateUser(userId, Map.of("ban_duration", PERMANENT_BAN));
    }

    public void unbanUser(UUID userId) {
        updateUser(userId, Map.of("ban_duration", "none"));
    }

    /**
     * GoTrue merge app_metadata theo key, nên chỉ ghi đè "role", giữ nguyên provider/providers.
     */
    public void setRole(UUID userId, String role) {
        updateUser(userId, Map.of("app_metadata", Map.of("role", role)));
    }

    private void updateUser(UUID userId, Map<String, Object> body) {
        if (serviceRoleKey.isBlank()) {
            log.warn("gotrue.service-role-key is not configured, skipped GoTrue update for user {}", userId);
            return;
        }

        try {
            rest.put()
                .uri("/admin/users/{id}", userId)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("GoTrue admin update failed for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider update failed");
        }
    }
}
