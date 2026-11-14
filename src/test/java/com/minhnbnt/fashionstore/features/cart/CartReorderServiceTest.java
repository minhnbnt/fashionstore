package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CartReorderServiceTest {

    @Autowired
    private CartService carts;

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
        users.upsert(id, new CreateUserRequest("re-" + id + "@t.local", "Buyer"));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    @Test
    void reorderHandlesCleanAddStockCeilingPricePreservationAndSkippedItems() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "shop-re-" + suffix);

        var pClean = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao Clean " + suffix,
                "ao-clean-" + suffix,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-CL-" + suffix, "M", "Trang", 120000L, null, 10, true))
            )
        );
        var vClean = catalog.getDetail(pClean.id()).variants().getFirst().id();

        var pCap = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao Cap " + suffix,
                "ao-cap-" + suffix,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-CP-" + suffix, "L", "Den", 100000L, null, 5, true))
            )
        );
        var vCap = catalog.getDetail(pCap.id()).variants().getFirst().id();

        var pHidden = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao Hidden " + suffix,
                "ao-hid-" + suffix,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-HD-" + suffix, "S", "Xam", 90000L, null, 10, true))
            )
        );
        var vHidden = catalog.getDetail(pHidden.id()).variants().getFirst().id();

        var order = orders.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(
                    new CheckoutItemRequest(vClean, 2),
                    new CheckoutItemRequest(vCap, 3),
                    new CheckoutItemRequest(vHidden, 1)
                ),
                "An",
                "0901",
                "Ha Noi",
                0L
            )
        );
        // After checkout: vClean=8, vCap=2, vHidden=9.
        // Add 1 unit of vCap into cart at 100k, then change live variant price to 150k.
        carts.addItem(buyerId, new CartItemRequest(vCap, 1));
        catalog.updateProduct(
            ownerId,
            shopId,
            pCap.id(),
            new UpdateProductRequest(
                null,
                null,
                null,
                null,
                List.of(new UpdateVariantRequest(vCap, null, null, null, 150000L, null, 2, true))
            )
        );
        catalog.updateProduct(
            ownerId,
            shopId,
            pHidden.id(),
            new UpdateProductRequest(null, null, null, "HIDDEN", null)
        );

        var res = carts.reorder(buyerId, order.id());

        assertThat(res.totalRequestedItems()).isEqualTo(3);
        assertThat(res.addedCount()).isEqualTo(2);
        assertThat(res.adjustedCount()).isEqualTo(1);
        assertThat(res.skippedCount()).isEqualTo(1);
        assertThat(res.adjustedItems()).hasSize(1);
        assertThat(res.adjustedItems().getFirst().variantId()).isEqualTo(vCap);
        assertThat(res.adjustedItems().getFirst().requestedQuantity()).isEqualTo(3);
        assertThat(res.adjustedItems().getFirst().addedQuantity()).isEqualTo(1);
        assertThat(res.skippedItems()).hasSize(1);
        assertThat(res.skippedItems().getFirst().variantId()).isEqualTo(vHidden);

        var capInCart = res.cart().items().stream()
            .filter(i -> vCap.equals(i.variantId()))
            .findFirst()
            .orElseThrow();
        assertThat(capInCart.quantity()).isEqualTo(2);
        // Existing cart item keeps original snapshot priceAtAdded (100_000) while unitPrice shows 150_000
        assertThat(capInCart.unitPrice()).isEqualTo(150000L);
        assertThat(capInCart.priceAtAdded()).isEqualTo(100000L);

        // Clean item still works cleanly & missing/wrong buyer returns 404
        assertThatThrownBy(() -> carts.reorder(UUID.randomUUID(), order.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        // Suspended shop returns 409 Conflict
        jdbc.sql("UPDATE shop SET status = 'SUSPENDED' WHERE id = ?").param(shopId).update();
        assertThatThrownBy(() -> carts.reorder(buyerId, order.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void reorderReturns200EvenWhenAllItemsAreSkipped() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "shop-re2-" + suffix);

        var pOut = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao Out " + suffix,
                "ao-out-" + suffix,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-OUT-" + suffix, "M", "Trang", 120000L, null, 1, true))
            )
        );
        var vOut = catalog.getDetail(pOut.id()).variants().getFirst().id();
        var order = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(vOut, 1)), "An", "0901", "HN", 0L)
        );

        var res = carts.reorder(buyerId, order.id());

        assertThat(res.addedCount()).isZero();
        assertThat(res.adjustedCount()).isZero();
        assertThat(res.skippedCount()).isEqualTo(1);
        assertThat(res.skippedItems()).hasSize(1);
        assertThat(res.skippedItems().getFirst().reason()).isEqualTo("OUT_OF_STOCK");
    }
}

