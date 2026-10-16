package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chạy mỗi request có JWT (qua AccountFilter): tạo user_profile nếu chưa có (JIT provisioning),
 * nâng role theo claim app_metadata của GoTrue, trả trạng thái tài khoản để filter chặn tài khoản khóa.
 * Không @Transactional: lỗi ở đây không được làm hỏng transaction của request.
 */
@Component
@RequiredArgsConstructor
public class AccountProvisioner {

    private final UserProfileRepository users;

    public AccountStatus ensureAccount(
        UUID id,
        @Nullable String email,
        String displayName,
        UserRole tokenRole
    ) {
        var profile = users.findById(id).orElse(null);

        if (profile == null) {
            if (email == null || email.isBlank()) {
                return AccountStatus.ACTIVE;
            }
            users.insertIfAbsent(id, email, displayName);
            profile = users.findById(id).orElse(null);
        }

        // Email đã thuộc profile khác (user GoTrue bị xóa rồi tạo lại): bỏ qua, không chặn request.
        if (profile == null) {
            return AccountStatus.ACTIVE;
        }

        if (tokenRole.compareTo(profile.getRole()) > 0) {
            users.updateRole(id, tokenRole.name());
        }

        return profile.getStatus();
    }
}
