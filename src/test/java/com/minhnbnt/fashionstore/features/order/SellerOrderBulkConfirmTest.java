package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SellerOrderBulkConfirmTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

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
    void bulkConfirmWithPartialSuccess() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-bulk-confirm");
        var v1 = variantInShop(ownerId, shopId, "ao-bulk-1", "SKU-B1", 10);
        var v2 = variantInShop(ownerId, shopId, "ao-bulk-2", "SKU-B2", 10);
        var v3 = variantInShop(ownerId, shopId, "ao-bulk-3", "SKU-B3", 10);

        var o1 = orders.checkout(buyerId, new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(v1, 1)), "B1", "0901", "HN", 0L));
        var o2 = orders.checkout(buyerId, new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(v2, 1)), "B2", "0902", "HN", 0L));
        var o3 = orders.checkout(buyerId, new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(v3, 1)), "B3", "0903", "HN", 0L));

        // o3 is cancelled
        orders.cancel(buyerId, o3.id());

        var nonExistentId = UUID.randomUUID();

        var bulkResponse = orders.bulkConfirm(ownerId, List.of(o1.id(), o2.id(), o3.id(), nonExistentId));

        assertThat(bulkResponse.totalRequested()).isEqualTo(4);
        assertThat(bulkResponse.successCount()).isEqualTo(2);
        assertThat(bulkResponse.failureCount()).isEqualTo(2);
        assertThat(bulkResponse.successfulOrderIds()).containsExactlyInAnyOrder(o1.id(), o2.id());
        assertThat(bulkResponse.failures()).hasSize(2);

        var updatedO1 = orders.getDetail(buyerId, o1.id());
        var updatedO2 = orders.getDetail(buyerId, o2.id());
        assertThat(updatedO1.status()).isEqualTo("CONFIRMED");
        assertThat(updatedO2.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void getOrderTimelineReturnsHistory() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-timeline");
        var v1 = variantInShop(ownerId, shopId, "ao-tl-1", "SKU-TL1", 10);

        var o = orders.checkout(buyerId, new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(v1, 1)), "B", "0901", "HN", 0L));
        orders.confirm(ownerId, o.id());
        orders.ship(ownerId, o.id());

        var timeline = orders.getOrderTimeline(ownerId, o.id());

        assertThat(timeline).isNotEmpty();
        var statuses = timeline.stream().map(com.minhnbnt.fashionstore.dto.admin.OrderTimelineResponse::toStatus).toList();
        assertThat(statuses).contains("PENDING", "CONFIRMED", "SHIPPING");
    }
}
