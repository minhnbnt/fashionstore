package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.identity.AccountInfo;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Danh bạ tài khoản website: tra trong user_profile trước, chỉ hỏi GoTrue khi DB chưa có mirror
 * và service-role key đã cấu hình.
 */
@Service
@RequiredArgsConstructor
public class IdentityDirectoryService {

    private final UserProfileRepository users;

    private final GoTrueAdminClient gotrue;

    @Transactional
    public Optional<AccountInfo> findByEmail(String email) {
        var normalized = email.trim().toLowerCase(Locale.ROOT);

        var profile = users.findByEmailIgnoreCase(normalized);
        if (profile.isPresent()) {
            return profile.map(IdentityDirectoryService::toAccountInfo);
        }

        if (!gotrue.isConfigured()) {
            return Optional.empty();
        }

        var userId = gotrue.findUserIdByEmail(normalized);
        if (userId.isEmpty()) {
            return Optional.empty();
        }

        users.insertIfAbsent(userId.get(), normalized, normalized);
        return users.findById(userId.get()).map(IdentityDirectoryService::toAccountInfo);
    }

    @Transactional(readOnly = true)
    public Optional<AccountInfo> findById(UUID id) {
        return users.findById(id).map(IdentityDirectoryService::toAccountInfo);
    }

    private static AccountInfo toAccountInfo(UserProfile profile) {
        return new AccountInfo(profile.getId(), profile.getEmail(), profile.getDisplayName());
    }
}
