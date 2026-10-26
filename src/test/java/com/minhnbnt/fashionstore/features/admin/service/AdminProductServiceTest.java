package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminProductServiceTest {

    @Autowired
    private AdminProductService adminProducts;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private OrderService orders;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private ProductResponse product(UUID ownerId, UUID shopId, String slug) {
        return catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true))
            )
        );
    }

    private static UpdateProductRequest statusChange(String status) {
        return new UpdateProductRequest(null, null, null, status, null);
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void banTakesProductOffMarketplace() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-off-market");
        var product = product(ownerId, shopId, "ban-off-market-ao");

        var banned = adminProducts.ban(
            adminId, product.id(), ProductViolationType.COUNTERFEIT, "Hàng nhái thương hiệu"
        );

        assertThat(banned.status()).isEqualTo("BANNED");
        assertThat(banned.violationType()).isEqualTo("COUNTERFEIT");
        assertThat(banned.statusReason()).isEqualTo("Hàng nhái thương hiệu");
        assertThatThrownBy(() -> catalog.getDetail(product.id()))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void bannedProductCannotBeCheckedOut() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-checkout");
        var product = product(ownerId, shopId, "ban-checkout-ao");
        var variantId = product.variants().get(0).id();
        adminProducts.ban(adminId, product.id(), ProductViolationType.PROHIBITED, "Hàng cấm kinh doanh");

        var request = new CheckoutRequest(
            shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L
        );

        assertThatThrownBy(() -> orders.checkout(buyerId, request))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void sellerCannotRelistBannedProduct() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-relist");
        var product = product(ownerId, shopId, "ban-relist-ao");
        adminProducts.ban(adminId, product.id(), ProductViolationType.MISLEADING, "Ảnh không đúng sản phẩm");

        assertThatThrownBy(() -> catalog.updateProduct(ownerId, shopId, product.id(), statusChange("ACTIVE")))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void banTwiceIsConflict() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-twice");
        var product = product(ownerId, shopId, "ban-twice-ao");
        adminProducts.ban(adminId, product.id(), ProductViolationType.SPAM, "Đăng trùng lặp");

        assertThatThrownBy(() -> adminProducts.ban(adminId, product.id(), ProductViolationType.SPAM, "Đăng lại"))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void banUnknownProductIsNotFound() {
        var adminId = mirroredUser();

        assertThatThrownBy(() -> adminProducts.ban(adminId, UUID.randomUUID(), ProductViolationType.SPAM, "Spam"))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void restoreReturnsProductToShopAsHidden() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-restore");
        var product = product(ownerId, shopId, "ban-restore-ao");
        adminProducts.ban(adminId, product.id(), ProductViolationType.WRONG_CATEGORY, "Sai danh mục");

        var restored = adminProducts.restore(adminId, product.id());

        assertThat(restored.status()).isEqualTo("HIDDEN");
        assertThat(restored.violationType()).isNull();
        assertThat(restored.statusReason()).isNull();

        var relisted = catalog.updateProduct(ownerId, shopId, product.id(), statusChange("ACTIVE"));
        assertThat(relisted.status()).isEqualTo("ACTIVE");
    }

    @Test
    void restoreNotBannedIsConflict() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "restore-active");
        var product = product(ownerId, shopId, "restore-active-ao");

        assertThatThrownBy(() -> adminProducts.restore(adminId, product.id()))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void detailHasShopVariantsAndModerationHistory() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-detail");
        var product = product(ownerId, shopId, "ban-detail-ao");
        adminProducts.ban(adminId, product.id(), ProductViolationType.IP_INFRINGEMENT, "Dùng logo thương hiệu");
        adminProducts.restore(adminId, product.id());

        var detail = adminProducts.getDetail(product.id());

        assertThat(detail.product().shopName()).isEqualTo("Shop ban-detail");
        assertThat(detail.product().minPrice()).isEqualTo(100000L);
        assertThat(detail.product().totalStock()).isEqualTo(10);
        assertThat(detail.variants()).hasSize(1);
        assertThat(detail.history()).hasSize(2);

        var ban = detail.history().get(0);
        assertThat(ban.fromStatus()).isEqualTo("ACTIVE");
        assertThat(ban.toStatus()).isEqualTo("BANNED");
        assertThat(ban.violationType()).isEqualTo("IP_INFRINGEMENT");
        assertThat(ban.reason()).isEqualTo("Dùng logo thương hiệu");
        assertThat(ban.changedByEmail()).isEqualTo("u-" + adminId + "@t.local");

        var restore = detail.history().get(1);
        assertThat(restore.fromStatus()).isEqualTo("BANNED");
        assertThat(restore.toStatus()).isEqualTo("HIDDEN");
    }

    @Test
    void detailUnknownProductIsNotFound() {
        assertThatThrownBy(() -> adminProducts.getDetail(UUID.randomUUID()))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void searchFiltersByStatusShopAndSku() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-search");
        var kept = product(ownerId, shopId, "ban-search-kept");
        var banned = product(ownerId, shopId, "ban-search-gone");
        adminProducts.ban(adminId, banned.id(), ProductViolationType.SPAM, "Đăng trùng lặp");

        var bannedPage = adminProducts.search(ProductStatus.BANNED, shopId, null, false, 0, 20);
        assertThat(bannedPage.total()).isEqualTo(1);
        assertThat(bannedPage.items().get(0).id()).isEqualTo(banned.id());

        var allInShop = adminProducts.search(null, shopId, null, false, 0, 20);
        assertThat(allInShop.total()).isEqualTo(2);

        var bySku = adminProducts.search(null, null, "sku-ban-search-kept", false, 0, 20);
        assertThat(bySku.items()).extracting(item -> item.id()).containsExactly(kept.id());
    }

    @Test
    void sellerSeesViolationOnBannedProduct() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "ban-seller-view");
        var product = product(ownerId, shopId, "ban-seller-view-ao");
        adminProducts.ban(adminId, product.id(), ProductViolationType.MISLEADING, "Giá niêm yết sai lệch");

        var bannedList = catalog.listSellerProducts(ownerId, shopId, "BANNED", null, 0, 20);

        assertThat(bannedList).hasSize(1);
        assertThat(bannedList.get(0).violationType()).isEqualTo("MISLEADING");
        assertThat(bannedList.get(0).statusReason()).isEqualTo("Giá niêm yết sai lệch");
    }
}
