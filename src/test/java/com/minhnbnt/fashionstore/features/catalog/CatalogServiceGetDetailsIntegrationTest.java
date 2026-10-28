package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
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

    private UUID shopId;
    private UUID categoryId;
    private UUID prod1Id; // đầy đủ dữ liệu: variant + image + 1 review 4 sao
    private UUID prod2Id; // có variant, không review

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
        insertImage(prod1Id, "shop/ao-day-du-1.jpg", 1);
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
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, description, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, 'ACTIVE', 'mô tả', :createdAt)
            """)
            .param("id", productId)
            .param("shopId", shopId)
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

    private void insertImage(UUID productId, String s3Key, int sortOrder) {
        jdbc.sql("""
            INSERT INTO product_image (id, product_id, s3_key, sort_order)
            VALUES (:id, :prodId, :s3Key, :sortOrder)
            """)
            .param("id", UUID.randomUUID())
            .param("prodId", productId)
            .param("s3Key", s3Key)
            .param("sortOrder", sortOrder)
            .update();
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
