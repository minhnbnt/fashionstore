package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CatalogServiceGetDetailsIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private com.minhnbnt.fashionstore.features.upload.S3UrlService s3Urls;

    private UUID shopId;
    private UUID categoryId;
    private UUID prod1Id; // đầy đủ dữ liệu: variant + image + 1 review 4 sao
    private UUID prod2Id; // có variant, không review
    private String prod1ImageKey;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        prod1Id = UUID.randomUUID();
        prod2Id = UUID.randomUUID();

        insertShop();
        insertCategory();
        insertProduct(prod1Id, "Áo Có Đầy Đủ Dữ Liệu", Instant.now().minusSeconds(1000));
        insertVariant(prod1Id, 250000, null);
        prod1ImageKey = insertImage(prod1Id, 1);
        insertBuyerAndReview(prod1Id, 4);
        insertProduct(prod2Id, "Áo Không Review", Instant.now().minusSeconds(500));
        insertVariant(prod2Id, 150000, 300000L);
    }

    @Test
    void matchesGetDetailForEachProductAndKeepsInputOrder() {
        var responses = catalogService.getDetails(List.of(prod2Id, prod1Id));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(prod2Id);
        assertThat(responses.get(1).id()).isEqualTo(prod1Id);
        assertThat(responses.get(0)).usingRecursiveComparison().isEqualTo(catalogService.getDetail(prod2Id));
        assertThat(responses.get(1)).usingRecursiveComparison().isEqualTo(catalogService.getDetail(prod1Id));
    }

    @Test
    void skipsMissingIdsAndKeepsOthers() {
        var responses = catalogService.getDetails(List.of(prod1Id, UUID.randomUUID(), prod2Id));

        assertThat(responses).extracting(ProductResponse::id).containsExactly(prod1Id, prod2Id);
    }

    @Test
    void returnsEmptyListForEmptyInput() {
        assertThat(catalogService.getDetails(List.of())).isEmpty();
    }

    @Test
    void skipsProductsFromShopsNotApproved() {
        var pendingShopId = UUID.randomUUID();
        jdbc.sql("INSERT INTO shop (id, name, slug, status) VALUES (:id, :name, :slug, 'PENDING')")
            .param("id", pendingShopId)
            .param("name", "Shop Chưa Duyệt")
            .param("slug", "shop-pending-" + pendingShopId)
            .update();
        var hiddenProdId = UUID.randomUUID();
        insertProduct(pendingShopId, hiddenProdId, "Áo Shop Chưa Duyệt", Instant.now().minusSeconds(100));

        var responses = catalogService.getDetails(List.of(prod1Id, hiddenProdId, prod2Id));

        assertThat(responses).extracting(ProductResponse::id).containsExactly(prod1Id, prod2Id);
    }

    @Test
    void summariesKeepInputOrderAndMapCheapestVariantAndFirstImage() {
        var summaries = catalogService.getSummaries(List.of(prod2Id, prod1Id));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).id()).isEqualTo(prod2Id);
        assertThat(summaries.get(0).minPrice()).isEqualTo(150000L);
        assertThat(summaries.get(0).defaultImageUrl()).isNull();
        assertThat(summaries.get(0).rank()).isNull();
        assertThat(summaries.get(1).id()).isEqualTo(prod1Id);
        assertThat(summaries.get(1).minPrice()).isEqualTo(250000L);
        assertThat(summaries.get(1).defaultImageUrl()).isEqualTo(s3Urls.publicUrl(prod1ImageKey));
    }

    @Test
    void summariesSkipMissingIdsAndEmptyInputIsEmpty() {
        assertThat(catalogService.getSummaries(List.of(prod1Id, UUID.randomUUID(), prod2Id)))
            .extracting(ProductSummaryResponse::id)
            .containsExactly(prod1Id, prod2Id);
        assertThat(catalogService.getSummaries(List.of())).isEmpty();
    }

    private void insertShop() {
        jdbc.sql("INSERT INTO shop (id, name, slug, status) VALUES (:id, :name, :slug, 'APPROVED')")
            .param("id", shopId)
            .param("name", "Shop Batch")
            .param("slug", "shop-batch-" + shopId)
            .update();
    }

    private void insertCategory() {
        jdbc.sql("INSERT INTO category (id, name, slug) VALUES (:id, :name, :slug)")
            .param("id", categoryId)
            .param("name", "Áo Batch")
            .param("slug", "ao-batch-" + categoryId)
            .update();
    }

    private void insertProduct(UUID productId, String name, Instant createdAt) {
        insertProduct(shopId, productId, name, createdAt);
    }

    private void insertProduct(UUID productShopId, UUID productId, String name, Instant createdAt) {
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, description, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, 'ACTIVE', 'mô tả', :createdAt)
            """)
            .param("id", productId)
            .param("shopId", productShopId)
            .param("catId", categoryId)
            .param("name", name)
            .param("slug", "prod-" + productId)
            .param("createdAt", Timestamp.from(createdAt))
            .update();
    }

    private void insertVariant(UUID productId, long price, @Nullable Long compareAtPrice) {
        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color,
                                         price, compare_at_price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'M', 'Đen', :price, :compareAtPrice, 10, true)
            """)
            .param("id", UUID.randomUUID())
            .param("shopId", shopId)
            .param("prodId", productId)
            .param("sku", "SKU-" + productId)
            .param("price", price)
            .param("compareAtPrice", compareAtPrice)
            .update();
    }

    /** Tạo row upload (s3_key UNIQUE nên key phải unique theo prodId) + product_image tham chiếu. */
    private String insertImage(UUID productId, int sortOrder) {
        var s3Key = "shop/" + productId + "-" + sortOrder + "-"
            + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        jdbc.sql("INSERT INTO upload (id, s3_key, content_type) VALUES (:id, :s3Key, 'image/jpeg')")
            .param("id", UUID.randomUUID())
            .param("s3Key", s3Key)
            .update();
        jdbc.sql("""
            INSERT INTO product_image (id, product_id, upload_id, sort_order)
            VALUES (:id, :prodId, (SELECT id FROM upload WHERE s3_key = :s3Key), :sortOrder)
            """)
            .param("id", UUID.randomUUID())
            .param("prodId", productId)
            .param("s3Key", s3Key)
            .param("sortOrder", sortOrder)
            .update();
        return s3Key;
    }

    private void insertBuyerAndReview(UUID productId, int stars) {
        var buyerId = UUID.randomUUID();
        jdbc.sql("INSERT INTO user_profile (id, email, display_name) VALUES (:id, :email, :name)")
            .param("id", buyerId)
            .param("email", "buyer-" + buyerId + "@test.local")
            .param("name", "Buyer")
            .update();
        jdbc.sql("""
            INSERT INTO review (id, product_id, buyer_id, buyer_display_name, stars, comment)
            VALUES (:id, :prodId, :buyerId, 'Buyer', :stars, 'Ổn')
            """)
            .param("id", UUID.randomUUID())
            .param("prodId", productId)
            .param("buyerId", buyerId)
            .param("stars", stars)
            .update();
    }
}
