package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderAutoCancelAtomicityTest {

    @Autowired
    private OrderAutoCancelJob job;

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

    @MockitoBean
    private NotificationService notificationService;

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
    void failedNotificationRollsBackWholeSystemCancel() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "shop-ac-atomic-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-ac-atomic-" + suffix, "SKU-AC-ATOMIC", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 3)), "An", "0901", "Ha Noi", 0L)
        );
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);

        jdbc.sql("UPDATE orders SET created_at = NOW() - INTERVAL '25 hours' WHERE id = ?")
            .param(created.id())
            .update();

        doThrow(new IllegalStateException("notification pipeline down"))
            .when(notificationService)
            .create(any(), any(), any(), any(), any(), any(), any());

        job.cancelExpiredPendingOrders();

        var after = orders.getDetail(buyerId, created.id());
        assertThat(after.status()).isEqualTo("PENDING");
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);
    }
}
