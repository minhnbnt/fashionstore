package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopServiceTest {

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private static CreateShopRequest createReq(String name, String slug) {
        return com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest(name, slug);
    }

    private static CreateShopRequest createReq(String name, String slug, String description) {
        return com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest(name, slug, description);
    }

    @Test
    void registerCreatesPendingShopWithOwnerMember() {
        var ownerId = mirroredUser();

        var created = shops.register(
            ownerId,
            createReq("Shop Mot", "shop-mot-reg", "Mo ta")
        );

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(shops.getMine(ownerId)).hasSize(1);
    }

    @Test
    void approveActivatesShop() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(
            ownerId,
            createReq("Shop Hai", "shop-hai-appr")
        );

        var approved = shops.approve(adminId, created.id());

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(shops.getBySlug("shop-hai-appr").id()).isEqualTo(created.id());
    }

    @Test
    void approveTwiceFails() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(
            ownerId,
            createReq("Shop Ba", "shop-ba-twice")
        );
        shops.approve(adminId, created.id());

        assertThatThrownBy(() -> shops.approve(adminId, created.id()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateByNonMemberFails() {
        var ownerId = mirroredUser();
        var created = shops.register(
            ownerId,
            createReq("Shop Nam", "shop-nam-stranger")
        );

        assertThatThrownBy(() -> shops.update(
            UUID.randomUUID(),
            created.id(),
            new UpdateShopRequest("Ten moi", null, null, null)
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getBySlugHidesPendingShop() {
        var ownerId = mirroredUser();
        shops.register(
            ownerId,
            createReq("Shop Sau", "shop-sau-hidden")
        );

        assertThatThrownBy(() -> shops.getBySlug("shop-sau-hidden"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void searchPendingShowsOnlyPendingShops() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        shops.register(ownerId, createReq("Cho Duyet", "cho-duyet"));
        var approved = shops.register(ownerId, createReq("Da Duyet", "da-duyet"));
        shops.approve(adminId, approved.id());

        var pending = shops.searchForAdmin(ShopStatus.PENDING, "duyet", 0, 50);

        assertThat(pending.items()).extracting(AdminShopResponse::slug).contains("cho-duyet");
        assertThat(pending.items()).extracting(AdminShopResponse::slug).doesNotContain("da-duyet");
    }

    @Test
    void searchMatchesOwnerEmailAndReturnsOwnerInfo() {
        var ownerId = mirroredUser();
        shops.register(ownerId, createReq("Shop Email", "shop-email-search"));

        var page = shops.searchForAdmin(null, "u-" + ownerId, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        var shop = page.items().getFirst();
        assertThat(shop.ownerId()).isEqualTo(ownerId);
        assertThat(shop.ownerEmail()).isEqualTo("u-" + ownerId + "@t.local");
        assertThat(shop.ownerStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void pendingQueueIsOldestSubmissionFirst() {
        var ownerId = mirroredUser();
        var token = "fifo-" + UUID.randomUUID().toString().substring(0, 8);
        var first = shops.register(ownerId, createReq("A", token + "-a"));
        var second = shops.register(ownerId, createReq("B", token + "-b"));

        var page = shops.searchForAdmin(ShopStatus.PENDING, token, 0, 20);

        assertThat(page.items()).extracting(AdminShopResponse::id).containsExactly(first.id(), second.id());
    }

    @Test
    void searchEscapesLikeWildcards() {
        var ownerId = mirroredUser();
        shops.register(ownerId, createReq("Wildcard", "wildcard-shop"));

        var page = shops.searchForAdmin(null, "wildcard%_", 0, 20);

        assertThat(page.total()).isZero();
    }

    @Test
    void rejectStoresReasonAndHistory() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, createReq("Tu Choi", "tu-choi-reason"));

        var rejected = shops.reject(adminId, created.id(), "Thiếu mô tả và logo shop");

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.statusReason()).isEqualTo("Thiếu mô tả và logo shop");
        var history = shops.historyOf(created.id());
        assertThat(history).extracting(StatusHistoryResponse::toStatus).containsExactly("PENDING", "REJECTED");
        assertThat(history.get(1).fromStatus()).isEqualTo("PENDING");
        assertThat(history.get(1).changedBy()).isEqualTo(adminId);
        assertThat(history.get(1).changedByEmail()).isEqualTo("u-" + adminId + "@t.local");
        assertThat(history.get(1).reason()).isEqualTo("Thiếu mô tả và logo shop");
    }

    @Test
    void ownerResubmitsRejectedShop() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, createReq("Gui Lai", "gui-lai-ok"));
        shops.reject(adminId, created.id(), "Thiếu mô tả shop");

        var resubmitted = shops.resubmit(ownerId, created.id());

        assertThat(resubmitted.status()).isEqualTo("PENDING");
        assertThat(resubmitted.statusReason()).isNull();
    }

    @Test
    void resubmitResetsKycStatusToPendingReview() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, ShopTestFixtures.createRequest("Gui Lai Kyc", "gui-lai-kyc"));
        shops.reject(adminId, created.id(), "Anh CCCD khong ro mat");

        var resubmitted = shops.resubmit(ownerId, created.id());

        assertThat(resubmitted.kycStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(resubmitted.kycRejectionReason()).isNull();
    }

    @Test
    void resubmitByNonOwnerFails() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, createReq("Gui Lai", "gui-lai-stranger"));
        shops.reject(adminId, created.id(), "Thiếu mô tả shop");

        assertThatThrownBy(() -> shops.resubmit(mirroredUser(), created.id()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void suspendHidesShopAndReinstateRestoresIt() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, createReq("Tam Khoa", "tam-khoa-shop"));
        shops.approve(adminId, created.id());

        var suspended = shops.suspend(adminId, created.id(), "Bán hàng giả nhãn hiệu");

        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(shops.isShopApproved(created.id())).isFalse();
        assertThatThrownBy(() -> shops.getBySlug("tam-khoa-shop")).isInstanceOf(ResponseStatusException.class);

        shops.reinstate(adminId, created.id());

        assertThat(shops.getBySlug("tam-khoa-shop").status()).isEqualTo("APPROVED");
    }

    @Test
    void suspendOwnedShopsTouchesOnlyApprovedShopsTheUserOwns() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var live = shops.register(ownerId, createReq("Live", "owned-live"));
        var waiting = shops.register(ownerId, createReq("Wait", "owned-wait"));
        shops.approve(adminId, live.id());

        var suspended = shops.suspendOwnedShops(ownerId, adminId, "Owner account locked: spam");

        assertThat(suspended).containsExactly(live.id());
        assertThat(shops.getForAdmin(live.id()).status()).isEqualTo("SUSPENDED");
        assertThat(shops.getForAdmin(waiting.id()).status()).isEqualTo("PENDING");
    }

    @Test
    void ownerOfReturnsOwner() {
        var ownerId = mirroredUser();
        var created = shops.register(ownerId, createReq("Owner", "owner-of-shop"));

        assertThat(shops.ownerOf(created.id())).isEqualTo(ownerId);
    }

    @Test
    void countByStatusCountsEveryStatus() {
        var ownerId = mirroredUser();
        shops.register(ownerId, createReq("Count", "count-shop"));

        var counts = shops.countByStatus();

        assertThat(counts).containsKeys(ShopStatus.values());
        assertThat(counts.get(ShopStatus.PENDING)).isPositive();
    }

    @Test
    void registerRejectsInvalidCccdFormat() {
        var ownerId = mirroredUser();
        var req = new CreateShopRequest(
            "Name", "slug-test", "desc", "logo", "banner", "0901234567", "Hanoi", "INDIVIDUAL",
            "NGUYEN VAN A", "123456", "01/01/2021", "CA", null, "f", "b", "s", null, "VCB", "123", "NGUYEN VAN A"
        );
        assertThatThrownBy(() -> shops.register(ownerId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void registerRejectsMismatchedBankHolder() {
        var ownerId = mirroredUser();
        var req = new CreateShopRequest(
            "Name", "slug-test", "desc", "logo", "banner", "0901234567", "Hanoi", "INDIVIDUAL",
            "NGUYEN VAN A", "001202001234", "01/01/2021", "CA", null, "f", "b", "s", null, "VCB", "123", "TRAN THI B"
        );
        assertThatThrownBy(() -> shops.register(ownerId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void registerRejectsDuplicateSlug() {
        var ownerId = mirroredUser();
        shops.register(ownerId, ShopTestFixtures.createRequest("Shop Truoc", "slug-trung"));

        assertThatThrownBy(() -> shops.register(mirroredUser(), ShopTestFixtures.createRequest("Shop Sau", "slug-trung")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void registerTrimsPaddedInputsAndNormalizesHolderName() {
        var ownerId = mirroredUser();
        var req = new CreateShopRequest(
            "  Shop Pad  ", "shop-pad", "  Mo ta pad  ", " logo.webp ", " banner.webp ",
            " 0901234567 ", " 123 Pho Hue ", "INDIVIDUAL",
            "  Nguyễn  Văn  Pad ", " 001202001234 ", " 01/01/2021 ", " Cục QLHC ",
            null, " front.webp ", " back.webp ", " selfie.webp ", null,
            " Vietcombank ", " 0123456789 ", " nguyen van pad "
        );

        var created = shops.register(ownerId, req);

        assertThat(created.name()).isEqualTo("Shop Pad");
        var page = shops.searchForAdmin(null, "shop-pad", 0, 20);
        assertThat(page.items().get(0).legalName()).isEqualTo("Nguyễn  Văn  Pad");
    }

    @Test
    void registerStoresBlankOptionalAsNull() {
        var ownerId = mirroredUser();
        var req = new CreateShopRequest(
            "Shop Blank", "shop-blank-opt", "Mo ta", "logo.webp", "banner.webp",
            "0901234567", "123 Pho Hue", "INDIVIDUAL",
            "NGUYEN VAN A", "001202001234", "01/01/2021", "Cuc QLHC",
            "   ", "front.webp", "back.webp", "selfie.webp", "   ",
            "Vietcombank", "0123456789", "NGUYEN VAN A"
        );

        shops.register(ownerId, req);

        var page = shops.searchForAdmin(null, "shop-blank-opt", 0, 20);
        assertThat(page.items().get(0).taxCode()).isNull();
        assertThat(page.items().get(0).businessCertKey()).isNull();
    }
}

