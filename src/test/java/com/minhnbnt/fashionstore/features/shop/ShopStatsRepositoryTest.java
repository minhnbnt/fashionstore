package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopStatsRepositoryTest {

    @Autowired
    private ShopStatsRepository statsRepository;

    @Autowired
    private JdbcClient jdbc;

    private UUID shopId;
    private UUID buyer1Id;
    private UUID buyer2Id;
    private UUID prod1Id;
    private UUID prod2Id;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        buyer1Id = UUID.randomUUID();
        buyer2Id = UUID.randomUUID();
        prod1Id = UUID.randomUUID();
        prod2Id = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        // 1. Users
        jdbc.sql("INSERT INTO user_profile (id, email, display_name) VALUES (:id, :email, :name)")
            .param("id", buyer1Id)
            .param("email", "b1-" + buyer1Id + "@test.local")
            .param("name", "Buyer One")
            .update();

        jdbc.sql("INSERT INTO user_profile (id, email, display_name) VALUES (:id, :email, :name)")
            .param("id", buyer2Id)
            .param("email", "b2-" + buyer2Id + "@test.local")
            .param("name", "Buyer Two")
            .update();

        // 2. Shop
        jdbc.sql("""
            INSERT INTO shop (id, name, slug, status)
            VALUES (:id, :name, :slug, 'APPROVED')
            """)
            .param("id", shopId)
            .param("name", "Test Shop " + shopId)
            .param("slug", "test-shop-" + shopId)
            .update();

        jdbc.sql("""
            INSERT INTO shop_member (shop_id, user_id, role)
            VALUES (:shopId, :userId, 'OWNER')
            """)
            .param("shopId", shopId)
            .param("userId", buyer1Id)
            .update();

        // 3. Category
        jdbc.sql("INSERT INTO category (id, name, slug) VALUES (:id, :name, :slug)")
            .param("id", categoryId)
            .param("name", "Ao Nam")
            .param("slug", "ao-nam-" + categoryId)
            .update();

        // 4. Products
        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, view_count)
            VALUES (:id, :shopId, :catId, :name, :slug, 'ACTIVE', 100)
            """)
            .param("id", prod1Id)
            .param("shopId", shopId)
            .param("catId", categoryId)
            .param("name", "Ao Thun Basic")
            .param("slug", "ao-thun-basic-" + prod1Id)
            .update();

        jdbc.sql("""
            INSERT INTO product (id, shop_id, category_id, name, slug, status, view_count)
            VALUES (:id, :shopId, :catId, :name, :slug, 'ACTIVE', 50)
            """)
            .param("id", prod2Id)
            .param("shopId", shopId)
            .param("catId", categoryId)
            .param("name", "Ao Khoac Jean")
            .param("slug", "ao-khoac-jean-" + prod2Id)
            .update();

        // 5. Variants
        var var1Id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color, price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'M', 'Black', 200000, 10, true)
            """)
            .param("id", var1Id)
            .param("shopId", shopId)
            .param("prodId", prod1Id)
            .param("sku", "SKU-" + var1Id)
            .update();

        var var2Id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO product_variant (id, shop_id, product_id, sku, size, color, price, stock_quantity, is_default)
            VALUES (:id, :shopId, :prodId, :sku, 'L', 'Blue', 500000, 0, true)
            """)
            .param("id", var2Id)
            .param("shopId", shopId)
            .param("prodId", prod2Id)
            .param("sku", "SKU-" + var2Id)
            .update();

        // 6. Orders: 3 today DELIVERED, 1 yesterday DELIVERED, 1 CANCELLED
        var now = Instant.now();
        var yesterday = now.minus(1, ChronoUnit.DAYS);

        createOrder(UUID.randomUUID(), buyer1Id, "DELIVERED", 200000L, now, prod1Id, var1Id, 1, 200000L);
        createOrder(UUID.randomUUID(), buyer1Id, "DELIVERED", 400000L, now, prod1Id, var1Id, 2, 400000L);
        createOrder(UUID.randomUUID(), buyer2Id, "DELIVERED", 500000L, now, prod2Id, var2Id, 1, 500000L);
        createOrder(UUID.randomUUID(), buyer2Id, "DELIVERED", 200000L, yesterday, prod1Id, var1Id, 1, 200000L);
        createOrder(UUID.randomUUID(), buyer1Id, "CANCELLED", 300000L, now, prod1Id, var1Id, 1, 300000L);
    }

    private void createOrder(UUID orderId, UUID buyerId, String status, long total, Instant createdAt,
                            UUID prodId, UUID varId, int qty, long lineTotal) {
        jdbc.sql("""
            INSERT INTO orders (id, code, shop_id, buyer_id, status, subtotal, shipping_fee, total,
                                shipping_name, shipping_phone, shipping_address, created_at)
            VALUES (:id, :code, :shopId, :buyerId, :status, :total, 0, :total,
                    'Test Buyer', '0901234567', '123 Test St', :createdAt)
            """)
            .param("id", orderId)
            .param("code", "ORD-" + orderId.toString().substring(0, 8))
            .param("shopId", shopId)
            .param("buyerId", buyerId)
            .param("status", status)
            .param("total", total)
            .param("createdAt", java.sql.Timestamp.from(createdAt))
            .update();

        jdbc.sql("""
            INSERT INTO order_item (id, order_id, product_id, variant_id, product_name, variant_label,
                                    sku, unit_price, quantity, line_total)
            VALUES (:id, :orderId, :prodId, :varId, 'Test Product', 'Default',
                    'SKU-TEST', :unitPrice, :qty, :lineTotal)
            """)
            .param("id", UUID.randomUUID())
            .param("orderId", orderId)
            .param("prodId", prodId)
            .param("varId", varId)
            .param("unitPrice", lineTotal / qty)
            .param("qty", qty)
            .param("lineTotal", lineTotal)
            .update();
    }

    @Test
    void todayStatsReturnsCorrectRevenue() {
        var stats = statsRepository.todayStats(shopId);

        // 3 DELIVERED orders today: 200k + 400k + 500k = 1,100,000
        assertThat(stats.todayRevenue()).isEqualTo(1100000L);
        assertThat(stats.todayOrders()).isEqualTo(3L);
        assertThat(stats.yesterdayRevenue()).isEqualTo(200000L);
        assertThat(stats.yesterdayOrders()).isEqualTo(1L);
    }

    @Test
    void topSellingReturnsOrderedByRevenue() {
        var top = statsRepository.topSellingProducts(shopId, 10);

        assertThat(top).isNotEmpty();
        // prod1 total: 200k + 400k + 200k (yesterday) = 800k. prod2 total: 500k.
        assertThat(top.get(0).productId()).isEqualTo(prod1Id);
        assertThat(top.get(0).totalRevenue()).isEqualTo(800000L);
    }

    @Test
    void customerOverviewCountsCorrectly() {
        var overview = statsRepository.customerOverview(shopId);

        // 2 buyers total, buyer1 has 2 completed orders, buyer2 has 2 completed orders -> returning = 2
        assertThat(overview.totalCustomers()).isEqualTo(2L);
        assertThat(overview.returningCustomers()).isEqualTo(2L);
        assertThat(overview.newCustomers()).isEqualTo(0L);
    }

    @Test
    void orderStatusSummaryIncludesCancelledValue() {
        var from = Instant.now().minus(2, ChronoUnit.DAYS);
        var to = Instant.now().plus(1, ChronoUnit.DAYS);
        var summary = statsRepository.orderStatusSummary(shopId, from, to);

        assertThat(summary.completedOrders()).isEqualTo(4L);
        assertThat(summary.completedValue()).isEqualTo(1300000L);
        assertThat(summary.cancelledOrders()).isEqualTo(1L);
        assertThat(summary.cancelledValue()).isEqualTo(300000L);
    }

    @Test
    void orderStatusSummaryCountsOrderWithApprovedReturnsOnce() {
        var from = Instant.now().minus(2, ChronoUnit.DAYS);
        var to = Instant.now().plus(1, ChronoUnit.DAYS);

        var orderId = UUID.randomUUID();
        createOrder(
            orderId, buyer1Id, "DELIVERED", 500000L, Instant.now(), prod1Id, variantIdOf(prod1Id), 1, 500000L
        );
        createReturnRequest(orderId, "APPROVED", 250000L);
        createReturnRequest(orderId, "APPROVED", 100000L);
        createReturnRequest(orderId, "REJECTED", 999999L);

        var summary = statsRepository.orderStatusSummary(shopId, from, to);

        assertThat(summary.completedOrders()).isEqualTo(5L);
        assertThat(summary.completedValue()).isEqualTo(1800000L);
        assertThat(summary.cancelledOrders()).isEqualTo(1L);
        assertThat(summary.cancelledValue()).isEqualTo(300000L);
        assertThat(summary.returnedOrders()).isEqualTo(1L);
        assertThat(summary.returnedValue()).isEqualTo(350000L);
    }

    @Test
    void emptyShopAggregatesReturnZeroes() {
        var emptyShopId = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO shop (id, name, slug, status)
            VALUES (:id, :name, :slug, 'APPROVED')
            """)
            .param("id", emptyShopId)
            .param("name", "Empty Shop " + emptyShopId)
            .param("slug", "empty-shop-" + emptyShopId)
            .update();

        var from = Instant.now().minus(1, ChronoUnit.DAYS);
        var to = Instant.now().plus(1, ChronoUnit.DAYS);

        var today = statsRepository.todayStats(emptyShopId);
        assertThat(today.todayRevenue()).isZero();
        assertThat(today.todayOrders()).isZero();
        assertThat(today.yesterdayRevenue()).isZero();
        assertThat(today.yesterdayOrders()).isZero();

        var summary = statsRepository.orderStatusSummary(emptyShopId, from, to);
        assertThat(summary.completedOrders()).isZero();
        assertThat(summary.completedValue()).isZero();
        assertThat(summary.cancelledOrders()).isZero();
        assertThat(summary.cancelledValue()).isZero();
        assertThat(summary.returnedOrders()).isZero();
        assertThat(summary.returnedValue()).isZero();

        var customers = statsRepository.customerOverview(emptyShopId);
        assertThat(customers.totalCustomers()).isZero();
        assertThat(customers.newCustomers()).isZero();
        assertThat(customers.returningCustomers()).isZero();

        assertThat(statsRepository.topSellingProducts(emptyShopId, 10)).isEmpty();
        assertThat(statsRepository.revenueByCategory(emptyShopId)).isEmpty();
        assertThat(statsRepository.revenueByPayment(emptyShopId)).isEmpty();
        assertThat(statsRepository.revenueByPeriod(emptyShopId, "day", from, to)).isEmpty();

        var performance = statsRepository.shopPerformance(emptyShopId);
        assertThat(performance.cancelRate()).isZero();
        assertThat(performance.onTimeDeliveryRate()).isEqualTo(1.0);
        assertThat(performance.avgRating()).isEqualTo(5.0);
    }

    @Test
    void revenueByPeriodBucketsByDay() {
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 100000L,
            Instant.parse("2026-10-05T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 100000L
        );
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 200000L,
            Instant.parse("2026-10-06T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 200000L
        );
        createOrder(
            UUID.randomUUID(), buyer1Id, "CANCELLED", 50000L,
            Instant.parse("2026-10-05T03:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 50000L
        );

        var rows = statsRepository.revenueByPeriod(
            shopId, "day", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z")
        );

        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::periodLabel)
            .containsExactly("2026-10-05", "2026-10-06");
        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::revenue)
            .containsExactly(100000L, 200000L);
        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::orders)
            .containsExactly(1L, 1L);
    }

    @Test
    void revenueByPeriodBucketsByWeek() {
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 100000L,
            Instant.parse("2026-10-06T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 100000L
        );
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 200000L,
            Instant.parse("2026-10-13T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 200000L
        );

        var rows = statsRepository.revenueByPeriod(
            shopId, "week", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")
        );

        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::periodLabel)
            .containsExactly("2026-W41", "2026-W42");
        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::revenue)
            .containsExactly(100000L, 200000L);
    }

    @Test
    void revenueByPeriodBucketsByMonth() {
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 100000L,
            Instant.parse("2026-03-15T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 100000L
        );
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 200000L,
            Instant.parse("2026-04-15T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 200000L
        );

        var rows = statsRepository.revenueByPeriod(
            shopId, "month", Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z")
        );

        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::periodLabel)
            .containsExactly("2026-03", "2026-04");
        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::revenue)
            .containsExactly(100000L, 200000L);
    }

    @Test
    void revenueByPeriodBucketsByYear() {
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 100000L,
            Instant.parse("2025-03-15T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 100000L
        );
        createOrder(
            UUID.randomUUID(), buyer1Id, "DELIVERED", 200000L,
            Instant.parse("2026-03-15T02:00:00Z"), prod1Id, variantIdOf(prod1Id), 1, 200000L
        );

        var rows = statsRepository.revenueByPeriod(
            shopId, "year", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z")
        );

        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::periodLabel)
            .containsExactly("2025", "2026");
        assertThat(rows).extracting(ShopStatsRepository.RevenuePeriodRow::revenue)
            .containsExactly(100000L, 200000L);
    }

    private UUID variantIdOf(UUID productId) {
        return jdbc.sql("SELECT id FROM product_variant WHERE product_id = :product LIMIT 1")
            .param("product", productId)
            .query(UUID.class)
            .single();
    }

    private void createReturnRequest(UUID orderId, String status, long refundAmount) {
        jdbc.sql("""
            INSERT INTO return_request (id, order_id, shop_id, buyer_id, reason, status, refund_amount)
            VALUES (:id, :orderId, :shopId, :buyerId, 'Damaged item', :status, :refund)
            """)
            .param("id", UUID.randomUUID())
            .param("orderId", orderId)
            .param("shopId", shopId)
            .param("buyerId", buyer1Id)
            .param("status", status)
            .param("refund", refundAmount)
            .update();
    }
}
