package com.minhnbnt.fashionstore.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gọi GoTrue Admin API (/admin/users) bằng service role key.
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

    private static final int SEARCH_PAGE_SIZE = 50;

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

    public boolean isConfigured() {
        return !serviceRoleKey.isBlank();
    }

    /**
     * GoTrue lọc {@code filter} theo kiểu "chứa chuỗi" trên email/tên, nên phải so khớp email chính xác ở đây.
     */
    public Optional<UUID> findUserIdByEmail(String email) {
        requireConfigured();

        GoTrueUserPage page;
        try {
            page = rest.get()
                .uri("/admin/users?filter={email}&per_page={size}", email, SEARCH_PAGE_SIZE)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .retrieve()
                .body(GoTrueUserPage.class);
        } catch (RestClientException e) {
            log.error("GoTrue admin user lookup failed for {}: {}", email, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider request failed");
        }

        if (page == null || page.users() == null) {
            return Optional.empty();
        }

        for (var user : page.users()) {
            if (email.equalsIgnoreCase(user.email())) {
                return Optional.of(user.id());
            }
        }
        return Optional.empty();
    }

    /**
     * Tạo user đã xác nhận email (đăng nhập được ngay) và gắn sẵn role trong app_metadata.
     */
    public UUID createUser(String email, String password, String displayName, String role) {
        requireConfigured();

        var body = Map.of(
            "email", email,
            "password", password,
            "email_confirm", true,
            "app_metadata", Map.of("role", role),
            "user_metadata", Map.of("full_name", displayName)
        );

        GoTrueUser created;
        try {
            created = rest.post()
                .uri("/admin/users")
                .header("Authorization", "Bearer " + serviceRoleKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GoTrueUser.class);
        } catch (RestClientException e) {
            log.error("GoTrue admin create user failed for {}: {}", email, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider request failed");
        }

        if (created == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider returned no user");
        }
        return created.id();
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

    private void requireConfigured() {
        if (serviceRoleKey.isBlank()) {
            throw new IllegalStateException("gotrue.service-role-key is not configured");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoTrueUser(UUID id, @Nullable String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoTrueUserPage(@Nullable List<GoTrueUser> users) {
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
