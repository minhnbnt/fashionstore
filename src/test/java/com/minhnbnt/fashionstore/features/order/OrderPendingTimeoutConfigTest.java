package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "fashionstore.order.pending-timeout-hours=1")
@Import(TestcontainersConfiguration.class)
class OrderPendingTimeoutConfigTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcClient jdbc;

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

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, String sku, int stock) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Den", 100000L, null, stock, true))
            )
        );
        var detail = catalog.getDetail(created.id());
        return detail.variants().get(0).id();
    }

    @Test
    void confirmRejectsOrderOlderThanConfiguredTimeout() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "shop-timeout-confirm-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-timeout-" + suffix, "SKU-TIMEOUT-C", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );
        jdbc.sql("UPDATE orders SET created_at = NOW() - INTERVAL '2 hours' WHERE id = ?")
            .param(created.id())
            .update();

        assertThatThrownBy(() -> orders.confirm(ownerId, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        var cancelled = orders.getDetail(buyerId, created.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledBy()).isEqualTo("SYSTEM");
    }

    @Test
    void findExpiredPendingOrdersHonorsConfiguredTimeout() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "shop-timeout-job-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-timeout-job-" + suffix, "SKU-TIMEOUT-J", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );
        jdbc.sql("UPDATE orders SET created_at = NOW() - INTERVAL '2 hours' WHERE id = ?")
            .param(created.id())
            .update();

        var expiredIds = orders.findExpiredPendingOrders().stream().map(Order::getId).toList();

        assertThat(expiredIds).contains(created.id());
    }

    private void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }
}
