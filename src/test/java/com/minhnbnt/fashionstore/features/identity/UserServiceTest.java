package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.Upload;
import com.minhnbnt.fashionstore.model.UserRole;
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
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountProvisioner provisioner;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private S3UrlService s3Urls;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        userService.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U"));
        return id;
    }

    /** Row upload thật trong DB để changeAvatar validate qua DB, không phải mock. */
    private UUID uploadOf(String contentType) {
        return uploads.save(Upload.create(UUID.randomUUID() + ".png", contentType)).getId();
    }

    @Test
    void upsertCreatesProfileOnFirstLogin() {
        var id = UUID.randomUUID();

        var created = userService.upsert(id, new CreateUserRequest("create@t.local", "An"));

        assertThat(created.id()).isEqualTo(id);
        assertThat(created.email()).isEqualTo("create@t.local");
        assertThat(created.avatarUrl()).isNull();
        assertThat(created.role()).isEqualTo("CUSTOMER");
        assertThat(created.status()).isEqualTo("ACTIVE");
    }

    @Test
    void upsertUpdatesProfileOnNextLogin() {
        var id = UUID.randomUUID();
        userService.upsert(id, new CreateUserRequest("update-old@t.local", "An"));

        var updated = userService.upsert(id, new CreateUserRequest("update-new@t.local", "Binh"));

        assertThat(updated.email()).isEqualTo("update-new@t.local");
        assertThat(updated.displayName()).isEqualTo("Binh");
    }

    @Test
    void changeAvatarStoresUploadReference() {
        var id = mirroredUser();
        var uploadId = uploadOf("image/png");
        var expectedUrl = s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());

        var updated = userService.changeAvatar(id, uploadId);

        assertThat(updated.avatarUrl()).isEqualTo(expectedUrl);
        assertThat(userService.getById(id).avatarUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void clearAvatarRemovesUrl() {
        var id = mirroredUser();
        userService.changeAvatar(id, uploadOf("image/png"));

        var cleared = userService.clearAvatar(id);

        assertThat(cleared.avatarUrl()).isNull();
        assertThat(userService.getById(id).avatarUrl()).isNull();
    }

    @Test
    void changeAvatarUnknownUserIsNotFound() {
        assertThatThrownBy(() -> userService.changeAvatar(UUID.randomUUID(), uploadOf("image/png")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void changeAvatarUnknownUploadIsBadRequest() {
        var id = mirroredUser();

        assertThatThrownBy(() -> userService.changeAvatar(id, UUID.randomUUID()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void changeAvatarRejectsVideoUpload() {
        var id = mirroredUser();

        assertThatThrownBy(() -> userService.changeAvatar(id, uploadOf("video/mp4")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void changeAvatarRejectsSvgUpload() {
        var id = mirroredUser();

        // Bucket public read: mở thẳng URL svg thì script trong file chạy được.
        assertThatThrownBy(() -> userService.changeAvatar(id, uploadOf("image/svg+xml")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upsertKeepsExistingAvatar() {
        var id = mirroredUser();
        var uploadId = uploadOf("image/png");
        var expectedUrl = s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());
        userService.changeAvatar(id, uploadId);

        var updated = userService.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U"));

        assertThat(updated.avatarUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void lockStoresReasonAndHistory() {
        var adminId = mirroredUser();
        var userId = mirroredUser();

        var locked = userService.lock(adminId, userId, "Gian lận hoàn tiền nhiều lần");

        assertThat(locked.status()).isEqualTo("LOCKED");
        assertThat(locked.statusReason()).isEqualTo("Gian lận hoàn tiền nhiều lần");
        var history = userService.historyOf(userId);
        assertThat(history).extracting(StatusHistoryResponse::toStatus).containsExactly("LOCKED");
        assertThat(history.getFirst().changedBy()).isEqualTo(adminId);
    }

    @Test
    void unlockRestoresAccount() {
        var adminId = mirroredUser();
        var userId = mirroredUser();
        userService.lock(adminId, userId, "Spam đánh giá");

        var unlocked = userService.unlock(adminId, userId);

        assertThat(unlocked.status()).isEqualTo("ACTIVE");
        assertThat(userService.historyOf(userId)).extracting(StatusHistoryResponse::toStatus)
            .containsExactly("LOCKED", "ACTIVE");
    }

    @Test
    void lockUnknownUserIsNotFound() {
        assertThatThrownBy(() -> userService.lock(mirroredUser(), UUID.randomUUID(), "Spam đánh giá"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void searchFiltersByQueryAndStatus() {
        var adminId = mirroredUser();
        var lockedId = mirroredUser();
        var activeId = mirroredUser();
        userService.lock(adminId, lockedId, "Spam đánh giá");

        var page = userService.searchForAdmin(AccountStatus.LOCKED, null, "u-" + lockedId, 0, 20);
        var none = userService.searchForAdmin(AccountStatus.LOCKED, null, "u-" + activeId, 0, 20);

        assertThat(page.items()).extracting(AdminUserResponse::id).containsExactly(lockedId);
        assertThat(none.total()).isZero();
    }

    @Test
    void grantRoleOnlyPromotes() {
        var userId = mirroredUser();

        assertThat(userService.grantRole(userId, UserRole.SHOP)).isTrue();
        assertThat(userService.grantRole(userId, UserRole.CUSTOMER)).isFalse();
        assertThat(userService.getForAdmin(userId).role()).isEqualTo("SHOP");
    }

    @Test
    void countByStatusCountsLockedAccounts() {
        var adminId = mirroredUser();
        var userId = mirroredUser();
        userService.lock(adminId, userId, "Spam đánh giá");

        var counts = userService.countByStatus();

        assertThat(counts).containsKeys(AccountStatus.values());
        assertThat(counts.get(AccountStatus.LOCKED)).isPositive();
    }

    @Test
    void provisionerCreatesProfileFromTokenOnFirstRequest() {
        var id = UUID.randomUUID();

        var status = provisioner.ensureAccount(id, "jit-" + id + "@t.local", "Jit", UserRole.CUSTOMER);

        assertThat(status).isEqualTo(AccountStatus.ACTIVE);
        assertThat(userService.getById(id).email()).isEqualTo("jit-" + id + "@t.local");
    }

    @Test
    void provisionerSyncsHigherRoleFromToken() {
        var id = mirroredUser();

        provisioner.ensureAccount(id, "u-" + id + "@t.local", "U", UserRole.ADMIN);

        assertThat(userService.getById(id).role()).isEqualTo("ADMIN");
    }

    @Test
    void provisionerReportsLockedAccount() {
        var adminId = mirroredUser();
        var userId = mirroredUser();
        userService.lock(adminId, userId, "Spam đánh giá");

        var status = provisioner.ensureAccount(userId, "u-" + userId + "@t.local", "U", UserRole.CUSTOMER);

        assertThat(status).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void provisionerSkipsTokenWithoutEmail() {
        var id = UUID.randomUUID();

        var status = provisioner.ensureAccount(id, null, "NoMail", UserRole.CUSTOMER);

        assertThat(status).isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> userService.getById(id)).isInstanceOf(ResponseStatusException.class);
    }
}
