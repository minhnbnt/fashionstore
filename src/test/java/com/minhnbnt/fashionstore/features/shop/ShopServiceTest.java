package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ShopStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

    @Test
    void registerCreatesPendingShopWithOwnerMember() {
        var ownerId = mirroredUser();

        var created = shops.register(
            ownerId,
            new CreateShopRequest("Shop Mot", "shop-mot-reg", "Mo ta", null, null)
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
            new CreateShopRequest("Shop Hai", "shop-hai-appr", null, null, null)
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
            new CreateShopRequest("Shop Ba", "shop-ba-twice", null, null, null)
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
            new CreateShopRequest("Shop Nam", "shop-nam-stranger", null, null, null)
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
            new CreateShopRequest("Shop Sau", "shop-sau-hidden", null, null, null)
        );

        assertThatThrownBy(() -> shops.getBySlug("shop-sau-hidden"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void searchPendingShowsOnlyPendingShops() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        shops.register(ownerId, new CreateShopRequest("Cho Duyet", "cho-duyet", null, null, null));
        var approved = shops.register(ownerId, new CreateShopRequest("Da Duyet", "da-duyet", null, null, null));
        shops.approve(adminId, approved.id());

        var pending = shops.searchForAdmin(ShopStatus.PENDING, "duyet", 0, 50);

        assertThat(pending.items()).extracting(AdminShopResponse::slug).contains("cho-duyet");
        assertThat(pending.items()).extracting(AdminShopResponse::slug).doesNotContain("da-duyet");
    }

    @Test
    void searchMatchesOwnerEmailAndReturnsOwnerInfo() {
        var ownerId = mirroredUser();
        shops.register(ownerId, new CreateShopRequest("Shop Email", "shop-email-search", null, null, null));

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
        var first = shops.register(ownerId, new CreateShopRequest("A", token + "-a", null, null, null));
        var second = shops.register(ownerId, new CreateShopRequest("B", token + "-b", null, null, null));

        var page = shops.searchForAdmin(ShopStatus.PENDING, token, 0, 20);

        assertThat(page.items()).extracting(AdminShopResponse::id).containsExactly(first.id(), second.id());
    }

    @Test
    void searchEscapesLikeWildcards() {
        var ownerId = mirroredUser();
        shops.register(ownerId, new CreateShopRequest("Wildcard", "wildcard-shop", null, null, null));

        var page = shops.searchForAdmin(null, "wildcard%_", 0, 20);

        assertThat(page.total()).isZero();
    }

    @Test
    void rejectStoresReasonAndHistory() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, new CreateShopRequest("Tu Choi", "tu-choi-reason", null, null, null));

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
        var created = shops.register(ownerId, new CreateShopRequest("Gui Lai", "gui-lai-ok", null, null, null));
        shops.reject(adminId, created.id(), "Thiếu mô tả shop");

        var resubmitted = shops.resubmit(ownerId, created.id());

        assertThat(resubmitted.status()).isEqualTo("PENDING");
        assertThat(resubmitted.statusReason()).isNull();
    }

    @Test
    void resubmitByNonOwnerFails() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, new CreateShopRequest("Gui Lai", "gui-lai-stranger", null, null, null));
        shops.reject(adminId, created.id(), "Thiếu mô tả shop");

        assertThatThrownBy(() -> shops.resubmit(mirroredUser(), created.id()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void suspendHidesShopAndReinstateRestoresIt() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(ownerId, new CreateShopRequest("Tam Khoa", "tam-khoa-shop", null, null, null));
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
        var live = shops.register(ownerId, new CreateShopRequest("Live", "owned-live", null, null, null));
        var waiting = shops.register(ownerId, new CreateShopRequest("Wait", "owned-wait", null, null, null));
        shops.approve(adminId, live.id());

        var suspended = shops.suspendOwnedShops(ownerId, adminId, "Owner account locked: spam");

        assertThat(suspended).containsExactly(live.id());
        assertThat(shops.getForAdmin(live.id()).status()).isEqualTo("SUSPENDED");
        assertThat(shops.getForAdmin(waiting.id()).status()).isEqualTo("PENDING");
    }

    @Test
    void ownerOfReturnsOwner() {
        var ownerId = mirroredUser();
        var created = shops.register(ownerId, new CreateShopRequest("Owner", "owner-of-shop", null, null, null));

        assertThat(shops.ownerOf(created.id())).isEqualTo(ownerId);
    }

    @Test
    void countByStatusCountsEveryStatus() {
        var ownerId = mirroredUser();
        shops.register(ownerId, new CreateShopRequest("Count", "count-shop", null, null, null));

        var counts = shops.countByStatus();

        assertThat(counts).containsKeys(ShopStatus.values());
        assertThat(counts.get(ShopStatus.PENDING)).isPositive();
    }
}
