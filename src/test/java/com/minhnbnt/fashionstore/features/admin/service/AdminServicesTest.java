package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminServicesTest {

    @Autowired
    private AdminShopService adminShops;

    @Autowired
    private AdminUserService adminUsers;

    @Autowired
    private AdminStatsService stats;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @MockitoBean
    private GoTrueAdminClient gotrue;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private UUID registerShop(UUID ownerId, String slug) {
        return shops.register(ownerId, new CreateShopRequest("Shop " + slug, slug, null, null, null)).id();
    }

    @Test
    void approvePromotesOwnerToShopRoleInDbAndGoTrue() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = registerShop(ownerId, "admin-approve-promote");

        var approved = adminShops.approve(adminId, shopId);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(users.getById(ownerId).role()).isEqualTo("SHOP");
        verify(gotrue).setRole(ownerId, "shop");
    }

    @Test
    void approveSkipsGoTrueWhenOwnerAlreadyHasHigherRole() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        users.grantRole(ownerId, UserRole.ADMIN);
        var shopId = registerShop(ownerId, "admin-approve-admin-owner");

        adminShops.approve(adminId, shopId);

        assertThat(users.getById(ownerId).role()).isEqualTo("ADMIN");
        verify(gotrue, never()).setRole(any(), any());
    }

    @Test
    void approveRollsBackWhenGoTrueFails() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = registerShop(ownerId, "admin-approve-rollback");
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider update failed"))
            .when(gotrue).setRole(any(), any());

        assertThatThrownBy(() -> adminShops.approve(adminId, shopId)).isInstanceOf(ResponseStatusException.class);

        assertThat(shops.getForAdmin(shopId).status()).isEqualTo("PENDING");
        assertThat(users.getById(ownerId).role()).isEqualTo("CUSTOMER");
    }

    @Test
    void shopDetailHasHistoryAndCounts() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = registerShop(ownerId, "admin-detail-history");
        adminShops.reject(adminId, shopId, "Thiếu mô tả shop");

        var detail = adminShops.getDetail(shopId);

        assertThat(detail.shop().status()).isEqualTo("REJECTED");
        assertThat(detail.history()).hasSize(2);
        assertThat(detail.productCount()).isZero();
        assertThat(detail.orderCount()).isZero();
    }

    @Test
    void lockBansInGoTrueAndSuspendsOwnedShops() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = registerShop(ownerId, "admin-lock-owner-shop");
        adminShops.approve(adminId, shopId);

        var locked = adminUsers.lock(adminId, ownerId, "Bán hàng cấm");

        assertThat(locked.status()).isEqualTo("LOCKED");
        assertThat(shops.getForAdmin(shopId).status()).isEqualTo("SUSPENDED");
        assertThat(shops.getForAdmin(shopId).statusReason()).isEqualTo("Owner account locked: Bán hàng cấm");
        verify(gotrue).banUser(ownerId);
    }

    @Test
    void adminCannotLockThemselves() {
        var adminId = mirroredUser();

        assertThatThrownBy(() -> adminUsers.lock(adminId, adminId, "Tự khóa mình"))
            .isInstanceOf(ResponseStatusException.class);
        verify(gotrue, never()).banUser(any());
    }

    @Test
    void unlockUnbansButKeepsShopsSuspended() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = registerShop(ownerId, "admin-unlock-owner-shop");
        adminShops.approve(adminId, shopId);
        adminUsers.lock(adminId, ownerId, "Bán hàng cấm");

        var unlocked = adminUsers.unlock(adminId, ownerId);

        assertThat(unlocked.status()).isEqualTo("ACTIVE");
        assertThat(shops.getForAdmin(shopId).status()).isEqualTo("SUSPENDED");
        verify(gotrue).unbanUser(ownerId);
    }

    @Test
    void userDetailListsShopsAndHistory() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        registerShop(ownerId, "admin-user-detail-shop");
        adminUsers.lock(adminId, ownerId, "Spam đánh giá");

        var detail = adminUsers.getDetail(ownerId);

        assertThat(detail.user().status()).isEqualTo("LOCKED");
        assertThat(detail.shops()).hasSize(1);
        assertThat(detail.orderCount()).isZero();
        assertThat(detail.history()).hasSize(1);
    }

    @Test
    void statsCountPendingShops() {
        var ownerId = mirroredUser();
        registerShop(ownerId, "admin-stats-pending");

        var result = stats.get();

        assertThat(result.pendingShops()).isPositive();
        assertThat(result.totalUsers()).isPositive();
        assertThat(result.totalShops()).isGreaterThanOrEqualTo(result.pendingShops());
    }

    @Test
    void statsCountOrdersAndTodayTrend() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = registerShop(ownerId, "admin-stats-orders");
        shops.approve(adminId, shopId);

        var product = catalog.createProduct(
            ownerId,
            shopId,
            new CreateProductRequest(
                "Ao stats",
                "admin-stats-orders-ao",
                null,
                null,
                List.of(new CreateVariantRequest("STATS-1", "M", "Den", 100000L, null, 10, true))
            )
        );
        var variantId = catalog.getDetail(product.id()).variants().get(0).id();
        var before = stats.get();

        orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 2)), "An", "0901", "Ha Noi", 0L)
        );
        var after = stats.get();

        assertThat(after.totalOrders()).isEqualTo(before.totalOrders() + 1);
        assertThat(after.pendingOrders()).isEqualTo(before.pendingOrders() + 1);
        assertThat(after.grossMerchandiseValue()).isEqualTo(before.grossMerchandiseValue() + 200000L);
        assertThat(after.collectedRevenue()).isEqualTo(before.collectedRevenue());

        var today = after.dailyTrend().get(AdminStatsService.TREND_DAYS - 1);
        var todayBefore = before.dailyTrend().get(AdminStatsService.TREND_DAYS - 1);
        assertThat(today.orders()).isEqualTo(todayBefore.orders() + 1);
        assertThat(today.newUsers()).isPositive();
        assertThat(today.newShops()).isPositive();
    }
}
