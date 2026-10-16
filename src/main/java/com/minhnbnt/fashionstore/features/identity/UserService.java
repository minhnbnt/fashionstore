package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserProfile;
import com.minhnbnt.fashionstore.model.UserRole;
import com.minhnbnt.fashionstore.model.UserStatusHistory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository users;

    private final UserStatusHistoryRepository histories;

    private final UserAdminQueryRepository adminQueries;

    @Transactional
    public UserResponse upsert(@NonNull UUID id, @NonNull CreateUserRequest request) {

        var existing = users.findLockedById(id);
        if (existing.isPresent()) {
            var profile = existing.get();
            profile.changeEmail(request.email());
            profile.rename(request.displayName());
            profile.changeAvatar(request.avatarKey());
            return toResponse(users.save(profile));
        }

        var created = UserProfile.create(
            id,
            request.email(),
            request.displayName(),
            request.avatarKey()
        );

        return toResponse(users.save(created));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {

        var profile = users.findById(id).orElse(null);
        if (profile == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Profile not found, call POST /api/users first"
            );
        }

        return toResponse(profile);
    }

    @Transactional
    public UserResponse lock(@NonNull UUID adminId, @NonNull UUID userId, @NonNull String reason) {
        var profile = lockedProfile(userId);

        profile.lock(reason);
        users.save(profile);

        histories.save(UserStatusHistory.record(
            userId,
            AccountStatus.ACTIVE,
            AccountStatus.LOCKED,
            adminId,
            reason
        ));
        return toResponse(profile);
    }

    @Transactional
    public UserResponse unlock(@NonNull UUID adminId, @NonNull UUID userId) {
        var profile = lockedProfile(userId);

        profile.unlock();
        users.save(profile);

        histories.save(UserStatusHistory.record(userId, AccountStatus.LOCKED, AccountStatus.ACTIVE, adminId, ""));
        return toResponse(profile);
    }

    /**
     * Nâng role (không hạ). Trả true nếu role thực sự đổi, để caller biết có cần đồng bộ GoTrue không.
     */
    @Transactional
    public boolean grantRole(@NonNull UUID userId, @NonNull UserRole role) {
        var profile = lockedProfile(userId);
        var before = profile.getRole();

        profile.grantRole(role);
        if (profile.getRole() == before) {
            return false;
        }

        users.save(profile);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> searchForAdmin(
        @Nullable AccountStatus status,
        @Nullable UserRole role,
        @Nullable String query,
        int page,
        int size
    ) {
        var items = adminQueries.search(status, role, query, page, size);
        var total = adminQueries.count(status, role, query);
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getForAdmin(@NonNull UUID userId) {
        var profile = users.findById(userId).orElse(null);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return new AdminUserResponse(
            profile.getId(),
            profile.getEmail(),
            profile.getDisplayName(),
            profile.getAvatarKey(),
            profile.getRole().name(),
            profile.getStatus().name(),
            profile.getStatusReason(),
            profile.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> historyOf(@NonNull UUID userId) {
        return adminQueries.history(userId);
    }

    @Transactional(readOnly = true)
    public Map<AccountStatus, Long> countByStatus() {
        return adminQueries.countByStatus();
    }

    private UserProfile lockedProfile(UUID userId) {
        var profile = users.findLockedById(userId).orElse(null);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return profile;
    }

    private static UserResponse toResponse(UserProfile profile) {
        return new UserResponse(
            profile.getId(),
            profile.getEmail(),
            profile.getDisplayName(),
            profile.getAvatarKey(),
            profile.getRole().name(),
            profile.getStatus().name(),
            profile.getStatusReason(),
            profile.getCreatedAt()
        );
    }
}
