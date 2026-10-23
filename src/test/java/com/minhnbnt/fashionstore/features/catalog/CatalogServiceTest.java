package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageItemRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductSearchRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductSort;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Category;
import com.minhnbnt.fashionstore.model.ProductStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private JdbcClient jdbc;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID testCategory() {
        return categories.findAllByOrderBySortOrderAsc().stream()
            .findFirst()
            .map(Category::getId)
            .orElseGet(() -> categories.save(Category.seed("Áo Nam", "ao-nam", 0)).getId());
    }

    private CreateProductRequest productRequest(String slug) {
        return new CreateProductRequest(
            "Áo Thun " + slug,
            slug,
            "Mô tả chi tiết " + slug,
            testCategory(),
            List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, false, null, null, null, null, null, null, null, null, "ACTIVE"
        );
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void createOnPendingShopIsForbidden() {
        var ownerId = mirroredUser();
        var shop = shops.register(ownerId, com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest("Shop Pend", "shop-pend-cat"));

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

        var hits = catalog.search(keyword("ao-search-hide-xyz"));

        assertThat(hits.items()).extracting(h -> h.id()).doesNotContain(created.id());
    }

    private UUID seededCategory(String slug) {
        return categories.save(Category.seed("Cat " + slug, slug, 99)).getId();
    }

    private static CreateVariantRequest variant(String sku, String size, String color, long price) {
        return new CreateVariantRequest(sku, size, color, price, null, 5, false);
    }

    private CreateProductRequest productIn(
        String slug,
        String name,
        @Nullable UUID categoryId,
        List<CreateVariantRequest> variants
    ) {
        var withDefault = new ArrayList<CreateVariantRequest>();
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            withDefault.add(new CreateVariantRequest(
                v.sku(),
                v.size(),
                v.color(),
                v.price(),
                v.compareAtPrice(),
                v.stockQuantity(),
                i == 0
            ));
        }
        var catId = categoryId != null ? categoryId : testCategory();
        return new CreateProductRequest(
            name,
            slug,
            "Mô tả chi tiết " + name,
            catId,
            withDefault,
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, false, null, null, null, null, null, null, null, null, "ACTIVE"
        );
    }

    private static ProductSearchRequest keyword(String q) {
        return new ProductSearchRequest(q, null, null, null, null, null, null, 0, 20);
    }

    private static ProductSearchRequest inCategory(String category) {
        return new ProductSearchRequest(null, category, null, null, null, null, null, 0, 20);
    }

    @Test
    void keywordSearchReturnsOnlyMatches() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-kw-only");
        var hit = catalog.createProduct(ownerId, shopId, productIn(
            "kw-hit", "Quylmatch", null, List.of(variant("KW-HIT", "M", "Den", 100000))
        ));
        var miss = catalog.createProduct(ownerId, shopId, productIn(
            "kw-miss", "Quylother", null, List.of(variant("KW-MISS", "M", "Den", 100000))
        ));

        var result = catalog.search(keyword("quylmatch"));

        assertThat(result.items()).extracting(h -> h.id()).contains(hit.id());
        assertThat(result.items()).extracting(h -> h.id()).doesNotContain(miss.id());
        assertThat(result.items().getFirst().rank()).isNotNull();
    }

    @Test
    void browseFiltersByCategoryWithoutKeyword() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-browse-cat");
        var categoryId = seededCategory("cat-browse");
        var inside = catalog.createProduct(ownerId, shopId, productIn(
            "browse-in", "Ao trong", categoryId, List.of(variant("BR-IN", "M", "Den", 100000))
        ));
        catalog.createProduct(ownerId, shopId, productIn(
            "browse-out", "Ao ngoai", null, List.of(variant("BR-OUT", "M", "Den", 100000))
        ));

        var result = catalog.search(inCategory("cat-browse"));

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).extracting(h -> h.id()).containsExactly(inside.id());
        assertThat(result.items().getFirst().rank()).isNull();
    }

    @Test
    void sizeColorAndPriceMustMatchTheSameVariant() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-variant-f");
        var categoryId = seededCategory("cat-variant");
        var created = catalog.createProduct(ownerId, shopId, productIn(
            "variant-f", "Ao bien the", categoryId, List.of(
                variant("VF-S-DEN", "S", "Den", 100000),
                variant("VF-L-TRANG", "L", "Trang", 300000)
            )
        ));

        var sameVariant = catalog.search(new ProductSearchRequest(
            null, "cat-variant", 200000L, null, "L", "Trang", null, 0, 20
        ));
        var mixedVariant = catalog.search(new ProductSearchRequest(
            null, "cat-variant", null, null, "L", "Den", null, 0, 20
        ));
        var tooCheap = catalog.search(new ProductSearchRequest(
            null, "cat-variant", null, 50000L, null, null, null, 0, 20
        ));

        assertThat(sameVariant.items()).extracting(h -> h.id()).containsExactly(created.id());
        assertThat(mixedVariant.items()).isEmpty();
        assertThat(tooCheap.items()).isEmpty();
    }

    @Test
    void sortsByPriceAndPaginates() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-sort-p");
        var categoryId = seededCategory("cat-sort");
        var mid = catalog.createProduct(ownerId, shopId, productIn(
            "sort-mid", "Ao mid", categoryId, List.of(variant("SORT-MID", "M", "Den", 200000))
        ));
        var cheap = catalog.createProduct(ownerId, shopId, productIn(
            "sort-cheap", "Ao cheap", categoryId, List.of(variant("SORT-CHEAP", "M", "Den", 100000))
        ));
        var pricey = catalog.createProduct(ownerId, shopId, productIn(
            "sort-pricey", "Ao pricey", categoryId, List.of(variant("SORT-PRICEY", "M", "Den", 300000))
        ));

        var ascending = catalog.search(new ProductSearchRequest(
            null, "cat-sort", null, null, null, null, ProductSort.PRICE_ASC, 0, 20
        ));
        var descendingSecondPage = catalog.search(new ProductSearchRequest(
            null, "cat-sort", null, null, null, null, ProductSort.PRICE_DESC, 1, 2
        ));

        assertThat(ascending.items()).extracting(h -> h.id())
            .containsExactly(cheap.id(), mid.id(), pricey.id());
        assertThat(descendingSecondPage.total()).isEqualTo(3L);
        assertThat(descendingSecondPage.items()).extracting(h -> h.id()).containsExactly(cheap.id());
    }

    @Test
    void searchWithInvertedPriceRangeIsBadRequest() {
        var request = new ProductSearchRequest(null, null, 500000L, 100000L, null, null, null, 0, 20);

        assertThatThrownBy(() -> catalog.search(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void facetsListSizesColorsAndPriceBounds() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-facets");
        var categoryId = seededCategory("cat-facets");
        catalog.createProduct(ownerId, shopId, productIn(
            "facet-a", "Ao facet a", categoryId, List.of(
                variant("FA-XL", "XL", "Trang", 300000),
                variant("FA-S", "S", "Den", 100000)
            )
        ));
        catalog.createProduct(ownerId, shopId, productIn(
            "facet-b", "Ao facet b", categoryId, List.of(variant("FB-M", "M", "Den", 150000))
        ));

        var facets = catalog.facets(null, "cat-facets");

        assertThat(facets.sizes()).containsExactly("S", "M", "XL");
        assertThat(facets.colors()).containsExactly("Den", "Trang");
        assertThat(facets.minPrice()).isEqualTo(100000L);
        assertThat(facets.maxPrice()).isEqualTo(300000L);
    }

    @Test
    void injectionPayloadsAreTreatedAsPlainValues() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-inj");
        var categoryId = seededCategory("cat-inj");
        var guard = catalog.createProduct(ownerId, shopId, productIn(
            "inj-guard", "Ao injguard", categoryId, List.of(variant("INJ-M", "M", "Den", 100000))
        ));
        var tautology = "x' OR '1'='1";
        var dropTable = "x'); DROP TABLE product; --";

        var byCategory = catalog.search(inCategory(tautology));
        var byKeyword = catalog.search(new ProductSearchRequest(
            "' OR 1=1 --", "cat-inj", null, null, null, null, null, 0, 20
        ));
        var bySize = catalog.search(new ProductSearchRequest(
            null, "cat-inj", null, null, tautology, null, null, 0, 20
        ));
        var byColor = catalog.search(new ProductSearchRequest(
            null, "cat-inj", null, null, null, dropTable, null, 0, 20
        ));
        var keywordDrop = catalog.search(keyword(dropTable));
        var operators = catalog.search(keyword("áo & | ! ( ) :* \\ ' \" ;"));
        var facets = catalog.facets(dropTable, tautology);

        assertThat(byCategory.items()).isEmpty();
        assertThat(byKeyword.items()).isEmpty();
        assertThat(bySize.items()).isEmpty();
        assertThat(byColor.items()).isEmpty();
        assertThat(keywordDrop.items()).extracting(h -> h.id()).doesNotContain(guard.id());
        assertThat(operators.items()).extracting(h -> h.id()).doesNotContain(guard.id());
        assertThat(facets.sizes()).isEmpty();
        assertThat(catalog.search(inCategory("cat-inj")).items()).extracting(h -> h.id())
            .containsExactly(guard.id());
    }

    @Test
    void detailExposesCategory() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-detail-cat");
        var categoryId = seededCategory("cat-detail");
        var created = catalog.createProduct(ownerId, shopId, productIn(
            "detail-cat", "Ao detail", categoryId, List.of(variant("DC-M", "M", "Den", 100000))
        ));

        var detail = catalog.getDetail(created.id());

        assertThat(detail.categoryId()).isEqualTo(categoryId);
    }

    @Test
    void productCreationStatusDependsOnShopTrust() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest("Shop Trust", "shop-trust-test"));
        shops.approve(adminId, shop.id());

        // Approved shop has kycStatus = VERIFIED, so it's trusted. Default status is ACTIVE
        var activeProd = catalog.createProduct(ownerId, shop.id(), productRequest("ao-trusted-active"));
        assertThat(activeProd.status()).isEqualTo("ACTIVE");

        // When trusted shop explicitly sets status = HIDDEN:
        var hiddenReq = new CreateProductRequest(
            "Ao Hidden Shop", "ao-hidden-shop", "Desc", testCategory(),
            List.of(new CreateVariantRequest("SKU-HIDDEN", "M", "Den", 100000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, false, null, null, null, null, null, null, null, null, "HIDDEN"
        );
        var hiddenProd = catalog.createProduct(ownerId, shop.id(), hiddenReq);
        assertThat(hiddenProd.status()).isEqualTo("HIDDEN");

        // Now set shop to untrusted (kyc_status = PENDING_REVIEW and is_trusted = false)
        jdbc.sql("UPDATE shop SET is_trusted = false, kyc_status = 'PENDING_REVIEW' WHERE id = :id")
            .param("id", shop.id())
            .update();

        var pendingProd = catalog.createProduct(ownerId, shop.id(), productRequest("ao-untrusted-pending"));
        assertThat(pendingProd.status()).isEqualTo("PENDING");
    }

    @Test
    void tryOnRequiresGarmentImage() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-try-on");

        var tryOnReqWithoutGarment = new CreateProductRequest(
            "Ao Thuc Te Ao", "ao-thuc-te-ao", "Mo ta", testCategory(),
            List.of(new CreateVariantRequest("SKU-TRY-ON", "M", "Trang", 120000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, true, null, null, null, null, null, null, null, null, "ACTIVE"
        );

        assertThatThrownBy(() -> catalog.createProduct(ownerId, shopId, tryOnReqWithoutGarment))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("Garment image is required");
            });
    }

    @Test
    void imageCountValidationMinMax() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-images-val");

        // Less than 3 images
        var twoImages = List.of(
            new ImageItemRequest("img1.jpg", null, 0),
            new ImageItemRequest("img2.jpg", null, 1)
        );
        var reqTwoImages = new CreateProductRequest(
            "Ao Hai Anh", "ao-hai-anh", "Mo ta", testCategory(),
            List.of(new CreateVariantRequest("SKU-TWO-IMG", "M", "Do", 150000L, null, 5, true)),
            twoImages, null, null, false, null, null, null, null, null, null, null, null, "ACTIVE"
        );
        assertThatThrownBy(() -> catalog.createProduct(ownerId, shopId, reqTwoImages))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("at least 3 images");
            });

        // More than 9 images
        var tenImages = new ArrayList<ImageItemRequest>();
        for (int i = 0; i < 10; i++) {
            tenImages.add(new ImageItemRequest("img" + i + ".jpg", null, i));
        }
        var reqTenImages = new CreateProductRequest(
            "Ao Muoi Anh", "ao-muoi-anh", "Mo ta", testCategory(),
            List.of(new CreateVariantRequest("SKU-TEN-IMG", "M", "Do", 150000L, null, 5, true)),
            tenImages, null, null, false, null, null, null, null, null, null, null, null, "ACTIVE"
        );
        assertThatThrownBy(() -> catalog.createProduct(ownerId, shopId, reqTenImages))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("cannot exceed 9 images");
            });
    }

    @Test
    void createProductRejectsNameTooShort() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-val-name");

        var req = new CreateProductRequest(
            "Ao", "ao-ngan", "Mo ta", testCategory(),
            List.of(new CreateVariantRequest("SKU-NAME", "M", "Den", 100000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, false, null, null, null, null, null, null, null, null, "ACTIVE"
        );

        assertThatThrownBy(() -> catalog.createProduct(ownerId, shopId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("Product name must be between 5 and 300 characters");
            });
    }

    @Test
    void createProductRejectsNonExistentCategory() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-val-cat");

        var req = new CreateProductRequest(
            "Áo Thun Nam", "ao-thun-nam-cat", "Mo ta", UUID.randomUUID(),
            List.of(new CreateVariantRequest("SKU-CAT", "M", "Den", 100000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, false, null, null, null, null, null, null, null, null, "ACTIVE"
        );

        assertThatThrownBy(() -> catalog.createProduct(ownerId, shopId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("Category does not exist");
            });
    }

    @Test
    void createProductRejectsInvalidPackaging() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-val-pkg");

        var req = new CreateProductRequest(
            "Áo Thun Nam", "ao-thun-nam-pkg", "Mo ta", testCategory(),
            List.of(new CreateVariantRequest("SKU-PKG", "M", "Den", 100000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, false, null, null, null, null,
            -10, 20, 10, 5, "ACTIVE"
        );

        assertThatThrownBy(() -> catalog.createProduct(ownerId, shopId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("Package weight must be greater than 0");
            });
    }

    @Test
    void adminCanApproveAndRejectPendingProduct() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest("Shop Moderation", "shop-moderation"));
        shops.approve(adminId, shop.id());

        // Make shop untrusted so products start as PENDING
        jdbc.sql("UPDATE shop SET is_trusted = false, kyc_status = 'PENDING_REVIEW' WHERE id = :id")
            .param("id", shop.id())
            .update();

        var prodToApprove = catalog.createProduct(ownerId, shop.id(), productRequest("ao-mod-approve"));
        assertThat(prodToApprove.status()).isEqualTo("PENDING");

        var approved = catalog.approveProduct(adminId, prodToApprove.id());
        assertThat(approved.status()).isEqualTo("ACTIVE");

        var prodToReject = catalog.createProduct(ownerId, shop.id(), productRequest("ao-mod-reject"));
        assertThat(prodToReject.status()).isEqualTo("PENDING");

        var rejected = catalog.rejectProduct(adminId, prodToReject.id(), "Ảnh sản phẩm không đạt chuẩn");
        assertThat(rejected.status()).isEqualTo("BANNED");
        assertThat(rejected.statusReason()).isEqualTo("Ảnh sản phẩm không đạt chuẩn");
    }

    @Test
    void sellerCanUpdateProductAndTrackAuditLogs() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-edit-audit");

        var created = catalog.createProduct(ownerId, shopId, productRequest("ao-edit-test"));
        var initialVariant = created.variants().getFirst();

        // 1. Update product info, stock, price and add a new variant
        var updateReq = new UpdateProductRequest(
            "Áo Thun Cập Nhật",
            "Mô tả mới đã cập nhật",
            created.categoryId(),
            "ACTIVE",
            List.of(
                new UpdateVariantRequest(
                    initialVariant.id(),
                    initialVariant.sku(),
                    "M",
                    "Trắng",
                    150000L,
                    180000L,
                    25,
                    true
                ),
                new UpdateVariantRequest(
                    null,
                    "SKU-NEW-VAR-" + UUID.randomUUID().toString().substring(0, 6),
                    "L",
                    "Đen",
                    160000L,
                    190000L,
                    40,
                    false
                )
            ),
            List.of(
                new ImageItemRequest("img-new-1.jpg", null, 0),
                new ImageItemRequest("img-new-2.jpg", null, 1)
            ),
            null,
            "garment-new.png",
            true,
            "100% Linen",
            "Việt Nam",
            "Giặt tay nhẹ nhàng",
            "Size M: 50-60kg",
            300, 30, 20, 5
        );

        var updated = catalog.updateProduct(ownerId, shopId, created.id(), updateReq);
        assertThat(updated.name()).isEqualTo("Áo Thun Cập Nhật");
        assertThat(updated.description()).isEqualTo("Mô tả mới đã cập nhật");
        assertThat(updated.videoKey()).isNull();
        assertThat(updated.garmentKey()).isEqualTo("garment-new.png");
        assertThat(updated.tryOnEnabled()).isTrue();
        assertThat(updated.material()).isEqualTo("100% Linen");
        assertThat(updated.weightGrams()).isEqualTo(300);
        assertThat(updated.images()).hasSize(2);
        assertThat(updated.variants()).hasSize(2);

        // 2. Check seller product detail
        var detail = catalog.getSellerProductDetail(ownerId, shopId, created.id());
        assertThat(detail.name()).isEqualTo("Áo Thun Cập Nhật");

        // 3. Check audit logs
        var auditLogs = catalog.getAuditLogs(ownerId, shopId, created.id());
        assertThat(auditLogs).isNotEmpty();
        var firstLog = auditLogs.getFirst();
        assertThat(firstLog.action()).isEqualTo("UPDATE_PRODUCT");
        assertThat(firstLog.changesSummary()).contains("Tồn kho");

        // 4. Toggle status
        var toggled = catalog.toggleProductStatus(ownerId, shopId, created.id());
        assertThat(toggled.status()).isEqualTo("HIDDEN");

        var toggledAgain = catalog.toggleProductStatus(ownerId, shopId, created.id());
        assertThat(toggledAgain.status()).isEqualTo("ACTIVE");

        var logsAfterToggle = catalog.getAuditLogs(ownerId, shopId, created.id());
        assertThat(logsAfterToggle).hasSizeGreaterThanOrEqualTo(3);
        assertThat(logsAfterToggle.getFirst().action()).isEqualTo("TOGGLE_STATUS");
    }
}
