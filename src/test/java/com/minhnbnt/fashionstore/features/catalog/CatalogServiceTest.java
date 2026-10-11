package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageItemRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
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
class CatalogServiceTest {

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
        var shop = shops.register(ownerId, new CreateShopRequest("Shop " + slug, slug, null, null, null));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private static CreateProductRequest productRequest(String slug) {
        return new CreateProductRequest(
            "Ao " + slug,
            slug,
            null,
            null,
            List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true))
        );
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void createOnPendingShopIsForbidden() {
        var ownerId = mirroredUser();
        var shop = shops.register(ownerId, new CreateShopRequest("Shop Pend", "shop-pend-cat", null, null, null));

        assertThatThrownBy(() -> catalog.createProduct(ownerId, shop.id(), productRequest("ao-pend")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));
    }

    @Test
    void updateAcrossShopsIsNotFound() {
        var ownerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-cross-a");
        var shopB = approvedShop(ownerId, "shop-cross-b");
        var created = catalog.createProduct(ownerId, shopA, productRequest("ao-cross"));

        assertThatThrownBy(() -> catalog.updateProduct(
            ownerId,
            shopB,
            created.id(),
            new UpdateProductRequest("Ten lac", null, null, null, null)
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void detailHidesHiddenProduct() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-hide-p");
        var created = catalog.createProduct(ownerId, shopId, productRequest("ao-hide"));
        catalog.updateProduct(ownerId, shopId, created.id(), new UpdateProductRequest(null, null, null, "HIDDEN", null));

        assertThatThrownBy(() -> catalog.getDetail(created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void attachImagesAcrossShopsIsNotFound() {
        var ownerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-img-a");
        var shopB = approvedShop(ownerId, "shop-img-b");
        var created = catalog.createProduct(ownerId, shopA, productRequest("ao-img"));

        assertThatThrownBy(() -> catalog.attachImages(
            ownerId,
            shopB,
            created.id(),
            new AttachImagesRequest(List.of(new ImageItemRequest("k1.jpg", null, 0)))
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void searchHidesHiddenProduct() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-search-h");
        var created = catalog.createProduct(ownerId, shopId, productRequest("ao-search-hide-xyz"));
        catalog.updateProduct(ownerId, shopId, created.id(), new UpdateProductRequest(null, null, null, "HIDDEN", null));

        var hits = catalog.search("ao-search-hide-xyz", 20);

        assertThat(hits).extracting(h -> h.id()).doesNotContain(created.id());
    }
}
