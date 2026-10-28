package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.shop.ShopCategorySummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopProductCollectionType;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopProductSort;
import org.jspecify.annotations.Nullable;
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
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color,
                                         price, compare_at_price, stock_quantity, is_default)
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
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color,
                                         price, compare_at_price, stock_quantity, is_default)
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

    @Test
    void filtersProductsByCategoryId() {
        var otherCategory = insertCategory("Quần Nam");
        var jeansId = insertProductInCategory(shopId, otherCategory, "Quần Jean Đen", "ACTIVE", 400000);

        var filtered = new ShopProductQueryRequest(null, null, otherCategory, null, 0, 10);
        assertThat(repository.findShopProductIds(shopId, filtered)).containsExactly(jeansId);
        assertThat(repository.countShopProducts(shopId, filtered)).isEqualTo(1L);

        var baseCategory = new ShopProductQueryRequest(null, null, categoryId, null, 0, 10);
        // Created_at DESC: prod2 (mới hơn) đứng trước prod1
        assertThat(repository.findShopProductIds(shopId, baseCategory)).containsExactly(prod2Id, prod1Id);
        assertThat(repository.countShopProducts(shopId, baseCategory)).isEqualTo(2L);
    }

    @Test
    void ordersShopCategoriesByProductCountDesc() {
        var lowCategory = insertCategory("Danh Mục Ít Hàng");
        insertProductInCategory(shopId, lowCategory, "SP Lẻ", "ACTIVE", 100000);
        var highCategory = insertCategory("Danh Mục Nhiều Hàng");
        insertProductInCategory(shopId, highCategory, "SP A", "ACTIVE", 100000);
        insertProductInCategory(shopId, highCategory, "SP B", "ACTIVE", 100000);
        insertProductInCategory(shopId, highCategory, "SP C", "ACTIVE", 100000);

        var categories = repository.findShopCategories(shopId);

        assertThat(categories).extracting(ShopCategorySummaryResponse::id)
            .containsExactly(highCategory, categoryId, lowCategory);
        assertThat(categories).extracting(ShopCategorySummaryResponse::productCount)
            .containsExactly(3L, 2L, 1L);
    }

    @Test
    void countsOnlyThisShopsActiveProductsPerCategory() {
        var otherShopId = insertShop("Shop Khác");
        var hiddenOnlyCategory = insertCategory("Danh Mục Chỉ Có Hàng Ẩn");
        insertProductInCategory(shopId, hiddenOnlyCategory, "SP Ẩn", "HIDDEN", 100000);
        var otherShopCategory = insertCategory("Danh Mục Của Shop Khác");
        insertProductInCategory(otherShopId, otherShopCategory, "SP Của Shop Khác", "ACTIVE", 100000);
        // Shop khác bán sản phẩm trong CÙNG danh mục (danh mục là taxonomy chung) — không được làm phồng count
        insertProductInCategory(otherShopId, categoryId, "SP Khác Cùng Danh Mục", "ACTIVE", 100000);

        var categories = repository.findShopCategories(shopId);

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).id()).isEqualTo(categoryId);
        assertThat(categories.get(0).productCount()).isEqualTo(2L);
    }

    @Test
    void excludesHiddenProductsWhenQueryingWithoutFilters() {
        var req = new ShopProductQueryRequest(null, null, null, null, 0, 10);

        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod2Id, prod1Id); // NEWEST trước, prod3 HIDDEN bị loại
    }

    @Test
    void searchesByDescriptionKeyword() {
        var prod4Id = insertProduct("Áo Len Ấm", "Vải cotton mềm mại thoáng mát", "ACTIVE", 200000L, null,
            Instant.now().minusSeconds(200));

        var req = new ShopProductQueryRequest("cotton", null, null, null, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod4Id);
    }

    @Test
    void ordersByBestSellerCountingNonCancelledOrders() {
        var buyerId = insertUser("buyer-best");
        // prod1 bán 5 (2 đơn DELIVERED), prod2 bán 1; đơn CANCELLED qty 10 của prod2 không được tính
        createOrder(buyerId, "DELIVERED", prod1Id, 3);
        createOrder(buyerId, "DELIVERED", prod1Id, 2);
        createOrder(buyerId, "DELIVERED", prod2Id, 1);
        createOrder(buyerId, "CANCELLED", prod2Id, 10);

        var req = new ShopProductQueryRequest(null, ShopProductCollectionType.BEST_SELLER, null, null, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod1Id, prod2Id);
    }

    @Test
    void ordersSaleOffByDiscountRateDesc() {
        var mildSaleId = insertProduct("Áo Sơ Mi Sale Nhẹ", null, "ACTIVE", 200000L, 250000L,
            Instant.now().minusSeconds(300));

        var req = new ShopProductQueryRequest(null, ShopProductCollectionType.SALE_OFF, null, null, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod2Id, mildSaleId); // prod2 giảm 50%, sản phẩm mới giảm 20%
    }

    @Test
    void ordersByPriceAscWhenSortIsPriceAsc() {
        var midId = insertProduct("Áo Giá Trung Bình", null, "ACTIVE", 200000L, null, Instant.now().minusSeconds(300));

        var req = new ShopProductQueryRequest(null, null, null, ShopProductSort.PRICE_ASC, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod2Id, midId, prod1Id);
    }

    @Test
    void ordersByPriceDescWhenSortIsPriceDesc() {
        var midId = insertProduct("Áo Giá Trung Bình", null, "ACTIVE", 200000L, null, Instant.now().minusSeconds(300));

        var req = new ShopProductQueryRequest(null, null, null, ShopProductSort.PRICE_DESC, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(prod1Id, midId, prod2Id);
    }

    @Test
    void paginatesWithOffset() {
        var newestId = insertProduct("Áo Mới Nhất", null, "ACTIVE", 100000L, null, Instant.now().minusSeconds(100));

        var page0 = repository.findShopProductIds(shopId, new ShopProductQueryRequest(null, null, null, null, 0, 2));
        var page1 = repository.findShopProductIds(shopId, new ShopProductQueryRequest(null, null, null, null, 1, 2));

        assertThat(page0).containsExactly(newestId, prod2Id);
        assertThat(page1).containsExactly(prod1Id);
    }

    @Test
    void clampsLimitOverMaxToTwelve() {
        insertFillerProducts(13);

        var productIds = repository.findShopProductIds(shopId,
            new ShopProductQueryRequest(null, null, null, null, 0, 51));

        assertThat(productIds).hasSize(12);
    }

    @Test
    void clampsNonPositiveLimitToTwelve() {
        insertFillerProducts(13);

        var productIds = repository.findShopProductIds(shopId,
            new ShopProductQueryRequest(null, null, null, null, 0, 0));

        assertThat(productIds).hasSize(12);
    }

    @Test
    void escapesLikeWildcardsInKeyword() {
        var percentId = insertProduct("Áo 100% Cotton", null, "ACTIVE", 100000L, null, Instant.now().minusSeconds(100));
        insertProduct("Áo 1000 Cotton", null, "ACTIVE", 100000L, null, Instant.now().minusSeconds(200));

        var req = new ShopProductQueryRequest("100%", null, null, null, 0, 10);
        var productIds = repository.findShopProductIds(shopId, req);

        assertThat(productIds).containsExactly(percentId); // "100%" phải khớp literal, không phải wildcard
    }

    private UUID insertProduct(String name, @Nullable String description, String status, long price,
                               @Nullable Long compareAtPrice, Instant createdAt) {
        var productId = UUID.randomUUID();
        var resolvedDescription = "";
        if (description != null) {
            resolvedDescription = description;
        }
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, description, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, :status, :description, :createdAt)
            """)
            .param("id", productId)
            .param("shopId", shopId)
            .param("catId", categoryId)
            .param("name", name)
            .param("slug", "prod-" + productId)
            .param("status", status)
            .param("description", resolvedDescription)
            .param("createdAt", java.sql.Timestamp.from(createdAt))
            .update();

        insertVariant(productId, price, compareAtPrice);
        return productId;
    }

    private void insertVariant(UUID prodId, long price, @Nullable Long compareAtPrice) {
        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color,
                                         price, compare_at_price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'M', 'Đen', :price, :compareAtPrice, 10, true)
            """)
            .param("id", UUID.randomUUID())
            .param("shopId", shopId)
            .param("prodId", prodId)
            .param("sku", "SKU-" + prodId)
            .param("price", price)
            .param("compareAtPrice", compareAtPrice)
            .update();
    }

    private UUID insertCategory(String name) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO category (id, name, slug) VALUES (:id, :name, :slug)")
            .param("id", id)
            .param("name", name)
            .param("slug", "cat-" + id)
            .update();
        return id;
    }

    private UUID insertShop(String name) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO shop (id, name, slug, status) VALUES (:id, :name, :slug, 'APPROVED')")
            .param("id", id)
            .param("name", name)
            .param("slug", "shop-" + id)
            .update();
        return id;
    }

    private UUID insertProductInCategory(UUID ownerShopId, UUID catId, String name, String status, long price) {
        var productId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, created_at)
            VALUES (:id, :shopId, :catId, :name, :slug, :status, :createdAt)
            """)
            .param("id", productId)
            .param("shopId", ownerShopId)
            .param("catId", catId)
            .param("name", name)
            .param("slug", "prod-" + productId)
            .param("status", status)
            .param("createdAt", java.sql.Timestamp.from(Instant.now().minusSeconds(3000)))
            .update();

        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color,
                                         price, compare_at_price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'M', 'Đen', :price, NULL, 10, true)
            """)
            .param("id", UUID.randomUUID())
            .param("shopId", ownerShopId)
            .param("prodId", productId)
            .param("sku", "SKU-" + productId)
            .param("price", price)
            .update();
        return productId;
    }

    private void insertFillerProducts(int count) {
        for (var i = 1; i <= count; i++) {
            insertProduct("Sản Phẩm Loại " + i, null, "ACTIVE", 100000L + i, null,
                Instant.now().minusSeconds(2000 + i));
        }
    }

    private UUID insertUser(String emailPrefix) {
        var userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO user_profile (id, email, display_name) VALUES (:id, :email, :name)")
            .param("id", userId)
            .param("email", emailPrefix + "-" + userId + "@test.local")
            .param("name", "Test User")
            .update();
        return userId;
    }

    private void createOrder(UUID buyerId, String status, UUID prodId, int qty) {
        var orderId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO orders (id, code, shop_id, buyer_id, status, subtotal, shipping_fee, total,
                                shipping_name, shipping_phone, shipping_address, created_at)
            VALUES (:id, :code, :shopId, :buyerId, :status, :total, 0, :total,
                    'Test Buyer', '0901234567', '123 Test St', now())
            """)
            .param("id", orderId)
            .param("code", "ORD-" + orderId.toString().substring(0, 8))
            .param("shopId", shopId)
            .param("buyerId", buyerId)
            .param("status", status)
            .param("total", 100000L * qty)
            .update();

        jdbc.sql("""
            INSERT INTO order_item (id, order_id, product_id, variant_id, product_name, variant_label,
                                    sku, unit_price, quantity, line_total)
            VALUES (:id, :orderId, :prodId, NULL, 'Test Product', 'Default',
                    'SKU-TEST', 100000, :qty, :lineTotal)
            """)
            .param("id", UUID.randomUUID())
            .param("orderId", orderId)
            .param("prodId", prodId)
            .param("qty", qty)
            .param("lineTotal", 100000L * qty)
            .update();
    }
}
