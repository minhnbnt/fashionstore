package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.admin.CreateBannedKeywordRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.admin.AdminProductService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import org.jspecify.annotations.Nullable;
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
class BannedKeywordServiceTest {

    @Autowired
    private AdminProductService adminProducts;

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
        var shop = shops.register(ownerId, com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private ProductResponse product(UUID ownerId, UUID shopId, String slug, String name, @Nullable String description) {
        return catalog.createProduct(
            ownerId,
            shopId,
            new CreateProductRequest(
                name,
                slug,
                description,
                null,
                List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true))
            )
        );
    }

    /** Từ khóa riêng cho từng test: bảng banned_keyword dùng chung cả context. */
    private static String uniqueWord(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void creatingProductWithBannedKeywordRaisesSystemFlag() {
        var word = uniqueWord("replica");
        adminProducts.addKeyword(mirroredUser(), new CreateBannedKeywordRequest(word, ProductViolationType.COUNTERFEIT));
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "kw-create");

        var flagged = product(ownerId, shopId, "kw-create-ao", "Giày " + word + " loại 1", null);
        var clean = product(ownerId, shopId, "kw-create-clean", "Giày chính hãng", null);

        var queue = adminProducts.search(null, shopId, null, true, 0, 20);
        assertThat(queue.items()).extracting(item -> item.id()).containsExactly(flagged.id());
        var row = queue.items().get(0);
        assertThat(row.systemFlagged()).isTrue();
        assertThat(row.topReportReason()).isEqualTo("COUNTERFEIT");
        assertThat(clean.status()).isEqualTo("ACTIVE");
        assertThat(flagged.status()).isEqualTo("ACTIVE");

        var report = adminProducts.getDetail(flagged.id()).reports().get(0);
        assertThat(report.source()).isEqualTo("SYSTEM");
        assertThat(report.reporterEmail()).isNull();
        assertThat(report.description()).contains(word);
    }

    @Test
    void editingDescriptionToIncludeKeywordRaisesFlagOnlyOnce() {
        var word = uniqueWord("fake");
        adminProducts.addKeyword(mirroredUser(), new CreateBannedKeywordRequest(word, ProductViolationType.COUNTERFEIT));
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "kw-edit");
        var product = product(ownerId, shopId, "kw-edit-ao", "Áo khoác", "Hàng đẹp");

        var edit = new UpdateProductRequest(null, "Hàng " + word + " giá rẻ", null, null, null);
        catalog.updateProduct(ownerId, shopId, product.id(), edit);
        catalog.updateProduct(ownerId, shopId, product.id(), edit);

        var row = adminProducts.search(null, shopId, null, true, 0, 20).items().get(0);
        assertThat(row.id()).isEqualTo(product.id());
        assertThat(row.openReportCount()).isEqualTo(1);
    }

    @Test
    void addingKeywordScansExistingProducts() {
        var word = uniqueWord("hangnhai");
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "kw-rescan");
        var active = product(ownerId, shopId, "kw-rescan-active", "Túi " + word, null);
        var hidden = product(ownerId, shopId, "kw-rescan-hidden", "Ví " + word, null);
        catalog.updateProduct(ownerId, shopId, hidden.id(), new UpdateProductRequest(null, null, null, "HIDDEN", null));
        var banned = product(ownerId, shopId, "kw-rescan-banned", "Mũ " + word, null);
        adminProducts.ban(adminId, banned.id(), ProductViolationType.SPAM, "Đăng trùng lặp");
        product(ownerId, shopId, "kw-rescan-inside", "Túi " + word + "xyz", null);

        var created = adminProducts.addKeyword(adminId, new CreateBannedKeywordRequest(word, ProductViolationType.COUNTERFEIT));

        assertThat(created.flaggedProducts()).isEqualTo(2);
        assertThat(created.keyword().keyword()).isEqualTo(word);
        assertThat(created.keyword().createdByEmail()).isEqualTo("u-" + adminId + "@t.local");
        var queue = adminProducts.search(null, shopId, null, true, 0, 20);
        assertThat(queue.items()).extracting(item -> item.id()).containsExactlyInAnyOrder(active.id(), hidden.id());
    }

    @Test
    void keywordIsUniqueIgnoringCase() {
        var word = uniqueWord("dup");
        adminProducts.addKeyword(mirroredUser(), new CreateBannedKeywordRequest(word, ProductViolationType.SPAM));

        var upper = new CreateBannedKeywordRequest("  " + word.toUpperCase() + " ", ProductViolationType.SPAM);
        assertThatThrownBy(() -> adminProducts.addKeyword(mirroredUser(), upper))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void keywordsCanBeListedAndDeleted() {
        var word = uniqueWord("del");
        var created = adminProducts.addKeyword(mirroredUser(), new CreateBannedKeywordRequest(word, ProductViolationType.PROHIBITED));
        var id = created.keyword().id();

        assertThat(adminProducts.listKeywords()).extracting(k -> k.keyword()).contains(word);

        adminProducts.deleteKeyword(id);

        assertThat(adminProducts.listKeywords()).extracting(k -> k.keyword()).doesNotContain(word);
        assertThatThrownBy(() -> adminProducts.deleteKeyword(id))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }
}
