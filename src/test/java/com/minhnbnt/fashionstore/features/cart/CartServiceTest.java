package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemResponse;
import com.minhnbnt.fashionstore.dto.cart.CheckoutBlockedItem;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductVariant;
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
class CartServiceTest {

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
    private ProductRepository products;

    @Autowired
    private ProductVariantRepository variants;

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

    private UUID pendingShop(UUID ownerId, String slug) {
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
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

    private UUID hiddenVariantInShop(UUID ownerId, UUID shopId, String slug, String sku) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Den", 100000L, null, 10, true))
            )
        );
        catalog.updateProduct(
            ownerId, shopId, created.id(), new UpdateProductRequest(null, null, null, "HIDDEN", null)
        );
        return variants.findByProductIdOrderByCreatedAtAsc(created.id()).get(0).getId();
    }

    private UUID variantInPendingShop(UUID ownerId, UUID shopId, String slug, String sku) {
        var product = products.save(Product.create(shopId, null, "Ao " + slug, slug, "", ownerId));
        var variant = variants.save(
            ProductVariant.create(shopId, product.getId(), sku, "M", "Den", 100000L, null, 10, true)
        );
        return variant.getId();
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    private static CartCheckoutRequest shipping() {
        return new CartCheckoutRequest("An", "0901", "Ha Noi", 0L, null);
    }

    private static CartCheckoutRequest shipping(List<UUID> variantIds) {
        return new CartCheckoutRequest("An", "0901", "Ha Noi", 0L, variantIds);
    }

    @Test
    void addItemCreatesCartAndMergesQuantity() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-a");
        var variantId = variantInShop(ownerId, shopId, "ao-cart-a", "SKU-CART-A", 10);

        carts.addItem(buyerId, new CartItemRequest(variantId, 2));
        var cart = carts.addItem(buyerId, new CartItemRequest(variantId, 3));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(5);
        assertThat(cart.subtotal()).isEqualTo(500000L);
    }

    @Test
    void addUnknownVariantFails() {
        var buyerId = mirroredUser();

        assertThatThrownBy(() -> carts.addItem(buyerId, new CartItemRequest(UUID.randomUUID(), 1)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void updateQuantityAndRemoveAndClear() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-b");
        var variantId = variantInShop(ownerId, shopId, "ao-cart-b", "SKU-CART-B", 10);
        carts.addItem(buyerId, new CartItemRequest(variantId, 2));

        var updated = carts.updateQuantity(buyerId, variantId, 4);
        assertThat(updated.items().get(0).quantity()).isEqualTo(4);

        var removed = carts.removeItem(buyerId, variantId);
        assertThat(removed.items()).isEmpty();

        carts.addItem(buyerId, new CartItemRequest(variantId, 1));
        var cleared = carts.clear(buyerId);
        assertThat(cleared.items()).isEmpty();
    }

    @Test
    void updateMissingItemFails() {
        var buyerId = mirroredUser();

        assertThatThrownBy(() -> carts.updateQuantity(buyerId, UUID.randomUUID(), 2))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void checkoutSplitsPerShopAndEmptiesCart() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-cart-c1");
        var shopB = approvedShop(ownerId, "shop-cart-c2");
        var variantA = variantInShop(ownerId, shopA, "ao-cart-c1", "SKU-CART-C1", 10);
        var variantB = variantInShop(ownerId, shopB, "ao-cart-c2", "SKU-CART-C2", 10);
        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(buyerId, shipping());

        assertThat(placed).hasSize(2);
        assertThat(placed).extracting(order -> order.shopId()).containsExactlyInAnyOrder(shopA, shopB);
        assertThat(placed).extracting(order -> order.status()).containsOnly("PENDING");
        assertThat(carts.getCart(buyerId).items()).isEmpty();
    }

    @Test
    void checkoutBlockedOnSoldOutPlacesNothing() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-d");
        var soldOut = variantInShop(ownerId, shopId, "ao-cart-d1", "SKU-CART-D1", 0);
        var fine = variantInShop(ownerId, shopId, "ao-cart-d2", "SKU-CART-D2", 10);
        carts.addItem(buyerId, new CartItemRequest(soldOut, 1));
        carts.addItem(buyerId, new CartItemRequest(fine, 1));

        assertThatThrownBy(() -> carts.checkout(buyerId, shipping()))
            .isInstanceOf(CartCheckoutBlockedException.class)
            .satisfies(e -> assertThat(((CartCheckoutBlockedException) e).getItems())
                .extracting(CheckoutBlockedItem::variantId)
                .containsExactly(soldOut));

        assertThat(orders.getMine(buyerId)).isEmpty();
        assertThat(carts.getCart(buyerId).items()).hasSize(2);
    }

    @Test
    void checkoutSelectedItemsOnlyKeepsRestInCart() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-cart-g1");
        var shopB = approvedShop(ownerId, "shop-cart-g2");
        var variantA = variantInShop(ownerId, shopA, "ao-cart-g1", "SKU-CART-G1", 10);
        var variantB = variantInShop(ownerId, shopB, "ao-cart-g2", "SKU-CART-G2", 10);
        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(buyerId, shipping(List.of(variantA)));

        assertThat(placed).hasSize(1);
        assertThat(placed.get(0).shopId()).isEqualTo(shopA);
        var remaining = carts.getCart(buyerId);
        assertThat(remaining.items()).extracting(CartItemResponse::variantId).containsExactly(variantB);
        assertThat(remaining.subtotal()).isEqualTo(200000L);
    }

    @Test
    void checkoutWithVariantNotInCartFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-h");
        var variantId = variantInShop(ownerId, shopId, "ao-cart-h", "SKU-CART-H", 10);
        carts.addItem(buyerId, new CartItemRequest(variantId, 1));

        assertThatThrownBy(() -> carts.checkout(buyerId, shipping(List.of(variantId, UUID.randomUUID()))))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        assertThat(orders.getMine(buyerId)).isEmpty();
        assertThat(carts.getCart(buyerId).items()).hasSize(1);
    }

    @Test
    void checkoutSelectedAcrossShopsSplitsAndKeepsRest() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-cart-i1");
        var shopB = approvedShop(ownerId, "shop-cart-i2");
        var variantA = variantInShop(ownerId, shopA, "ao-cart-i1", "SKU-CART-I1", 10);
        var variantB = variantInShop(ownerId, shopB, "ao-cart-i2", "SKU-CART-I2", 10);
        var leftOut = variantInShop(ownerId, shopA, "ao-cart-i3", "SKU-CART-I3", 10);
        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));
        carts.addItem(buyerId, new CartItemRequest(leftOut, 1));

        var placed = carts.checkout(buyerId, shipping(List.of(variantA, variantB)));

        assertThat(placed).hasSize(2);
        assertThat(placed).extracting(order -> order.shopId()).containsExactlyInAnyOrder(shopA, shopB);
        assertThat(carts.getCart(buyerId).items())
            .extracting(CartItemResponse::variantId)
            .containsExactly(leftOut);
    }

    @Test
    void checkoutSelectedBlockedOnSoldOutPlacesNothing() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-j");
        var soldOut = variantInShop(ownerId, shopId, "ao-cart-j1", "SKU-CART-J1", 0);
        var fine = variantInShop(ownerId, shopId, "ao-cart-j2", "SKU-CART-J2", 10);
        var leftOut = variantInShop(ownerId, shopId, "ao-cart-j3", "SKU-CART-J3", 10);
        carts.addItem(buyerId, new CartItemRequest(soldOut, 1));
        carts.addItem(buyerId, new CartItemRequest(fine, 1));
        carts.addItem(buyerId, new CartItemRequest(leftOut, 1));

        assertThatThrownBy(() -> carts.checkout(buyerId, shipping(List.of(soldOut, fine))))
            .isInstanceOf(CartCheckoutBlockedException.class)
            .satisfies(e -> assertThat(((CartCheckoutBlockedException) e).getItems())
                .extracting(CheckoutBlockedItem::variantId)
                .containsExactly(soldOut));

        assertThat(orders.getMine(buyerId)).isEmpty();
        assertThat(carts.getCart(buyerId).items()).hasSize(3);
    }

    @Test
    void checkoutWithEmptySelectionChecksEverything() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-cart-k1");
        var shopB = approvedShop(ownerId, "shop-cart-k2");
        var variantA = variantInShop(ownerId, shopA, "ao-cart-k1", "SKU-CART-K1", 10);
        var variantB = variantInShop(ownerId, shopB, "ao-cart-k2", "SKU-CART-K2", 10);
        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(buyerId, shipping(List.of()));

        assertThat(placed).hasSize(2);
        assertThat(placed).extracting(order -> order.shopId()).containsExactlyInAnyOrder(shopA, shopB);
        assertThat(carts.getCart(buyerId).items()).isEmpty();
    }

    @Test
    void checkoutWithDuplicateSelectionChecksItemOnce() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-l");
        var variantA = variantInShop(ownerId, shopId, "ao-cart-l1", "SKU-CART-L1", 10);
        var variantB = variantInShop(ownerId, shopId, "ao-cart-l2", "SKU-CART-L2", 10);
        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(buyerId, shipping(List.of(variantA, variantA)));

        assertThat(placed).hasSize(1);
        assertThat(carts.getCart(buyerId).items())
            .extracting(CartItemResponse::variantId)
            .containsExactly(variantB);
        assertThat(carts.getCart(buyerId).subtotal()).isEqualTo(200000L);
    }

    @Test
    void checkoutEmptyCartFails() {
        var buyerId = mirroredUser();

        assertThatThrownBy(() -> carts.checkout(buyerId, shipping()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void checkoutBlockedForUnapprovedShop() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = pendingShop(ownerId, "shop-cart-e");
        var variantId = variantInPendingShop(ownerId, shopId, "ao-cart-e", "SKU-CART-E");
        carts.addItem(buyerId, new CartItemRequest(variantId, 1));

        assertThatThrownBy(() -> carts.checkout(buyerId, shipping()))
            .isInstanceOf(CartCheckoutBlockedException.class)
            .satisfies(e -> assertThat(((CartCheckoutBlockedException) e).getItems())
                .extracting(CheckoutBlockedItem::variantId)
                .containsExactly(variantId));

        assertThat(orders.getMine(buyerId)).isEmpty();
    }

    @Test
    void checkoutBlockedForHiddenProduct() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-cart-f");
        var variantId = hiddenVariantInShop(ownerId, shopId, "ao-cart-f", "SKU-CART-F");
        carts.addItem(buyerId, new CartItemRequest(variantId, 1));

        assertThatThrownBy(() -> carts.checkout(buyerId, shipping()))
            .isInstanceOf(CartCheckoutBlockedException.class)
            .satisfies(e -> assertThat(((CartCheckoutBlockedException) e).getItems())
                .extracting(CheckoutBlockedItem::variantId)
                .containsExactly(variantId));

        assertThat(orders.getMine(buyerId)).isEmpty();
    }
}
