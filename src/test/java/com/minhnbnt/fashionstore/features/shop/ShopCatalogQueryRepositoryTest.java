package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.shop.ShopProductCollectionType;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopCatalogQueryRepositoryTest {

    @Autowired
    private ShopCatalogQueryRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private UUID shopId;
    private UUID categoryId;
    private UUID prod1Id;
    private UUID prod2Id;
    private UUID prod3Id;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        prod1Id = UUID.randomUUID();
        prod2Id = UUID.randomUUID();
        prod3Id = UUID.randomUUID();

        // 1. Tạo category
        jdbc.sql("INSERT INTO category (id, name, slug) VALUES (:id, :name, :slug)")
            .param("id", categoryId)
            .param("name", "Áo Nam")
            .param("slug", "ao-nam-" + categoryId)
            .update();

        // 2. Tạo shop
        jdbc.sql("INSERT INTO shop (id, name, slug, status) VALUES (:id, :name, :slug, 'APPROVED')")
            .param("id", shopId)
            .param("name", "Shop Test")
            .param("slug", "shop-test-" + shopId)
            .update();

        // 3. Tạo sản phẩm:
        // prod1: Áo Polo Thể Thao (ACTIVE, không sale)
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, 'ACTIVE', :createdAt)
            """)
            .param("id", prod1Id)
            .param("shopId", shopId)
            .param("catId", categoryId)
            .param("name", "Áo Polo Thể Thao Cao Cấp")
            .param("slug", "ao-polo-" + prod1Id)
            .param("createdAt", java.sql.Timestamp.from(Instant.now().minusSeconds(1000)))
            .update();

        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color, price, compare_at_price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'L', 'Đen', 250000, NULL, 50, true)
            """)
            .param("id", UUID.randomUUID())
            .param("shopId", shopId)
            .param("prodId", prod1Id)
            .param("sku", "SKU-" + prod1Id)
            .update();

        // prod2: Áo Thun Cổ Tròn (ACTIVE, có SALE_OFF: compare_at_price 300000 > price 150000)
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, 'ACTIVE', :createdAt)
            """)
            .param("id", prod2Id)
            .param("shopId", shopId)
            .param("catId", categoryId)
            .param("name", "Áo Thun Cổ Tròn Mùa Hè")
            .param("slug", "ao-thun-" + prod2Id)
            .param("createdAt", java.sql.Timestamp.from(Instant.now().minusSeconds(500)))
            .update();

        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color, price, compare_at_price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'M', 'Trắng', 150000, 300000, 20, true)
            """)
            .param("id", UUID.randomUUID())
            .param("shopId", shopId)
            .param("prodId", prod2Id)
            .param("sku", "SKU-" + prod2Id)
            .update();

        // prod3: Quần Khaki (HIDDEN - không được xuất hiện trong catalog công khai)
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, 'HIDDEN', :createdAt)
            """)
            .param("id", prod3Id)
            .param("shopId", shopId)
            .param("catId", categoryId)
            .param("name", "Quần Khaki Slim-fit")
            .param("slug", "quan-khaki-" + prod3Id)
            .param("createdAt", java.sql.Timestamp.from(Instant.now()))
            .update();
    }

    @Test
    void searchesProductsByKeywordWithinShop() {
        var req = new ShopProductQueryRequest("Polo", null, null, null, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod1Id);
    }

    @Test
    void filtersProductsBySaleOffCollection() {
        var req = new ShopProductQueryRequest(null, ShopProductCollectionType.SALE_OFF, null, null, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod2Id);
    }

    @Test
    void findsShopCategoriesWithProductCount() {
        var categories = repository.findShopCategories(shopId);

        assertThat(categories).isNotEmpty();
        var cat = categories.stream().filter(c -> c.id().equals(categoryId)).findFirst();
        assertThat(cat).isPresent();
        assertThat(cat.get().productCount()).isEqualTo(2); // prod1 và prod2 (prod3 HIDDEN)
    }
}
