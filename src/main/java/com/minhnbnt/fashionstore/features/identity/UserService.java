package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository users;

    private final UserStatusHistoryRepository histories;

    private final UserAdminQueryRepository adminQueries;

    private final UploadRepository uploads;

    private final S3UrlService s3Urls;

    @Transactional
    public UserResponse upsert(@NonNull UUID id, @NonNull CreateUserRequest request) {

        var existing = users.findLockedById(id);
        if (existing.isPresent()) {
            var profile = existing.get();
            // Avatar chỉ đổi qua /api/users/me/avatar — mirror GoTrue không được đụng tới.
            profile.changeEmail(request.email());
            profile.rename(request.displayName());
            return toResponse(users.save(profile));
        }

        var created = UserProfile.create(id, request.email(), request.displayName());

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
    public UserResponse changeAvatar(@NonNull UUID id, @NonNull UUID avatarUploadId) {
        validateAvatarUpload(avatarUploadId);
        var profile = lockedProfile(id);

        profile.changeAvatar(avatarUploadId);
        return toResponse(users.save(profile));
    }

    @Transactional
    public UserResponse clearAvatar(@NonNull UUID id) {
        var profile = lockedProfile(id);

        profile.changeAvatar(null);
        return toResponse(users.save(profile));
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
            avatarUrlOf(profile),
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

    @Transactional(readOnly = true)
    public Map<UserRole, Long> countByRole() {
        return adminQueries.countByRole();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> countCreatedPerDay(@NonNull Instant from, @NonNull ZoneId zone) {
        return adminQueries.countCreatedPerDay(from, zone);
    }

    private UserProfile lockedProfile(UUID userId) {
        var profile = users.findLockedById(userId).orElse(null);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return profile;
    }

    /**
     * Upload phải tồn tại và content-type {@code image/*}. Bỏ {@code image/svg+xml} vì
     * bucket public read, mở thẳng URL thì script trong file chạy được (giữ hành vi cũ
     * khi validate theo extension).
     */
    private void validateAvatarUpload(UUID avatarUploadId) {

        var upload = uploads.findById(avatarUploadId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Avatar upload not found"));

        var contentType = upload.getContentType();
        if (!contentType.startsWith("image/") || contentType.equals("image/svg+xml")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Avatar upload is not a supported image");
        }
    }

    private @Nullable String avatarUrlOf(UserProfile profile) {

        var uploadId = profile.getAvatarUploadId();
        if (uploadId == null) {
            return null;
        }

        // FK bảo đảm upload tồn tại; thiếu là lỗi dữ liệu, fail loud.
        return s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());
    }

    private UserResponse toResponse(UserProfile profile) {
        return new UserResponse(
            profile.getId(),
            profile.getEmail(),
            profile.getDisplayName(),
            avatarUrlOf(profile),
            profile.getRole().name(),
            profile.getStatus().name(),
            profile.getStatusReason(),
            profile.getCreatedAt()
        );
    }
}
