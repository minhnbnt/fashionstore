package com.minhnbnt.fashionstore.features.wishlist;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.wishlist.repository.WishlistRepository;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.model.WishlistItem;
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
class WishlistServiceTest {

    @Autowired
    private WishlistService wishlist;

    @Autowired
    private WishlistRepository items;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ProductVariantRepository variants;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    private UUID user() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = user();
        var shop = shops.register(ownerId, new CreateShopRequest("Shop " + slug, slug, null, null, null));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID pendingShop(UUID ownerId, String slug) {
        var shop = shops.register(ownerId, new CreateShopRequest("Shop " + slug, slug, null, null, null));
        return shop.id();
    }

    private UUID productInShop(UUID ownerId, UUID shopId, String slug, String sku) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            new CreateProductRequest(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Den", 100000L, null, 10, true))
            )
        );
        return created.id();
    }

    private UUID productRowInShop(UUID ownerId, UUID shopId, String slug, String sku) {
        var product = products.save(Product.create(shopId, null, "Ao " + slug, slug, "", ownerId));
        var variant = variants.save(ProductVariant.create(shopId, product.getId(), sku, "M", "Den", 100000L, null, 10, true));
        return product.getId();
    }

    private void hideProduct(UUID ownerId, UUID shopId, UUID productId) {
        catalog.updateProduct(ownerId, shopId, productId, new UpdateProductRequest(null, null, null, "HIDDEN", null));
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void toggleAddsAndRemoves() {
        var ownerId = user();
        var buyerId = user();
        var shopId = approvedShop(ownerId, "shop-wish-a");
        var productId = productInShop(ownerId, shopId, "ao-wish-a", "SKU-WISH-A");

        var added = wishlist.toggle(buyerId, productId);
        assertThat(added.saved()).isTrue();
        assertThat(wishlist.getWishlist(buyerId)).hasSize(1);

        var removed = wishlist.toggle(buyerId, productId);
        assertThat(removed.saved()).isFalse();
        assertThat(wishlist.getWishlist(buyerId)).isEmpty();
    }

    @Test
    void toggleUnknownProductFails() {
        var buyerId = user();

        assertThatThrownBy(() -> wishlist.toggle(buyerId, UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void toggleHiddenProductFails() {
        var ownerId = user();
        var buyerId = user();
        var shopId = approvedShop(ownerId, "shop-wish-b");
        var productId = productInShop(ownerId, shopId, "ao-wish-b", "SKU-WISH-B");
        hideProduct(ownerId, shopId, productId);

        assertThatThrownBy(() -> wishlist.toggle(buyerId, productId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void toggleUnapprovedShopProductFails() {
        var ownerId = user();
        var buyerId = user();
        var shopId = pendingShop(ownerId, "shop-wish-c");
        var productId = productRowInShop(ownerId, shopId, "ao-wish-c", "SKU-WISH-C");

        assertThatThrownBy(() -> wishlist.toggle(buyerId, productId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void listShowsLiveData() {
        var ownerId = user();
        var buyerId = user();
        var shopId = approvedShop(ownerId, "shop-wish-d");
        var productId = productInShop(ownerId, shopId, "ao-wish-d", "SKU-WISH-D");
        wishlist.toggle(buyerId, productId);

        var list = wishlist.getWishlist(buyerId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).productId()).isEqualTo(productId);
        assertThat(list.get(0).name()).isEqualTo("Ao ao-wish-d");
        assertThat(list.get(0).minPrice()).isEqualTo(100000L);
        assertThat(list.get(0).available()).isTrue();
        assertThat(list.get(0).unavailableReason()).isNull();
    }

    @Test
    void listFlagsHiddenProduct() {
        var ownerId = user();
        var buyerId = user();
        var shopId = approvedShop(ownerId, "shop-wish-e");
        var productId = productInShop(ownerId, shopId, "ao-wish-e", "SKU-WISH-E");
        wishlist.toggle(buyerId, productId);
        hideProduct(ownerId, shopId, productId);

        var list = wishlist.getWishlist(buyerId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).name()).isEqualTo("Ao ao-wish-e");
        assertThat(list.get(0).available()).isFalse();
        assertThat(list.get(0).unavailableReason()).isNotNull();
        assertThat(list.get(0).minPrice()).isNull();
    }

    @Test
    void listFlagsUnapprovedShopProduct() {
        var ownerId = user();
        var buyerId = user();
        var shopId = pendingShop(ownerId, "shop-wish-f");
        var productId = productRowInShop(ownerId, shopId, "ao-wish-f", "SKU-WISH-F");
        items.save(WishlistItem.create(buyerId, productId));

        var list = wishlist.getWishlist(buyerId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).available()).isFalse();
        assertThat(list.get(0).unavailableReason()).isNotNull();
    }

    @Test
    void removeDeletes() {
        var ownerId = user();
        var buyerId = user();
        var shopId = approvedShop(ownerId, "shop-wish-g");
        var productId = productInShop(ownerId, shopId, "ao-wish-g", "SKU-WISH-G");
        wishlist.toggle(buyerId, productId);

        wishlist.remove(buyerId, productId);

        assertThat(wishlist.getWishlist(buyerId)).isEmpty();
    }

    @Test
    void removeMissingFails() {
        var buyerId = user();

        assertThatThrownBy(() -> wishlist.remove(buyerId, UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }
}
