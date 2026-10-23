package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.features.identity.AccountProvisioner;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tạo tài khoản admin đầu tiên lúc khởi động, kiểu KEYCLOAK_ADMIN / GF_SECURITY_ADMIN_USER.
 * Bật bằng {@code app.bootstrap-admin.email}; mật khẩu nên truyền qua biến môi trường.
 * Idempotent: email đã có trong GoTrue thì chỉ đảm bảo role admin, không đổi mật khẩu.
 * Lỗi (GoTrue chưa lên, thiếu key) chỉ log, không chặn app khởi động.
 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "email")
@Slf4j
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final String ADMIN_ROLE = "admin";

    private final GoTrueAdminClient gotrue;

    private final AccountProvisioner provisioner;

    private final String email;

    private final String password;

    private final String displayName;

    public AdminAccountBootstrap(
        GoTrueAdminClient gotrue,
        AccountProvisioner provisioner,
        @Value("${app.bootstrap-admin.email}") String email,
        @Value("${app.bootstrap-admin.password:}") String password,
        @Value("${app.bootstrap-admin.display-name:Admin}") String displayName
    ) {
        this.gotrue = gotrue;
        this.provisioner = provisioner;
        this.email = email.strip();
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            return;
        }
        if (!gotrue.isConfigured()) {
            log.warn("Skipped admin bootstrap for {}: gotrue.service-role-key is not configured", email);
            return;
        }

        try {
            ensureAdmin();
        } catch (RuntimeException e) {
            log.error("Admin bootstrap for {} failed: {}", email, e.getMessage());
        }
    }

    private void ensureAdmin() {
        var existingId = gotrue.findUserIdByEmail(email).orElse(null);

        if (existingId != null) {
            gotrue.setRole(existingId, ADMIN_ROLE);
            mirrorProfile(existingId);
            log.info("Admin bootstrap: {} already exists, ensured admin role", email);
            return;
        }

        if (password.isBlank()) {
            log.warn("Skipped admin bootstrap for {}: app.bootstrap-admin.password is empty", email);
            return;
        }

        var createdId = gotrue.createUser(email, password, displayName, ADMIN_ROLE);
        mirrorProfile(createdId);
        log.info("Admin bootstrap: created admin account {}", email);
    }

    private void mirrorProfile(UUID userId) {
        provisioner.ensureAccount(userId, email, displayName, UserRole.ADMIN);
    }
}
