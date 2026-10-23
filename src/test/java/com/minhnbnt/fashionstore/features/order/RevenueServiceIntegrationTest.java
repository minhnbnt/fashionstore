package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderItem;
import com.minhnbnt.fashionstore.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RevenueServiceIntegrationTest {

    @Autowired
    private RevenueService revenue;

    @Autowired
    private UserService users;

    @Autowired
    private ShopService shops;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderItemRepository items;

    @Autowired
    private ProductRepository products;

    @Autowired
    private JdbcClient jdbc;

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

    private UUID deliveredOrder(UUID shopId, UUID buyerId, long subtotal, String code) {
        var order = Order.create(code, shopId, buyerId, subtotal, 0L, "An", "0901", "Ha Noi");
        order.confirm();
        order.ship();
        order.deliver();
        return orders.save(order).getId();
    }

    private void setCreatedAt(UUID orderId, String isoInstant) {
        var at = OffsetDateTime.ofInstant(Instant.parse(isoInstant), ZoneOffset.UTC);
        jdbc.sql("UPDATE orders SET created_at = :at, updated_at = :at WHERE id = :id")
            .param("at", at)
            .param("id", orderId)
            .update();
    }

    @Test
    void dailyRevenueGroupsByUtcDayOnlyDeliveredOrdersNewestFirst() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "rev-daily");
        var buyerId = mirroredUser();

        var sameDayMorning = deliveredOrder(shopId, buyerId, 100000L, "ORD-DAY01");
        var sameDayEvening = deliveredOrder(shopId, buyerId, 50000L, "ORD-DAY02");
        var previousDay = deliveredOrder(shopId, buyerId, 70000L, "ORD-DAY03");
        var pending = Order.create("ORD-DAY04", shopId, buyerId, 999999L, 0L, "An", "0901", "Ha Noi");
        orders.save(pending);

        setCreatedAt(sameDayMorning, "2026-10-22T01:00:00Z");
        setCreatedAt(sameDayEvening, "2026-10-22T20:00:00Z");
        setCreatedAt(previousDay, "2026-10-21T23:00:00Z");

        var rows = revenue.getDailyRevenue(ownerId, shopId);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).date()).isEqualTo("2026-10-22");
        assertThat(rows.get(0).revenue()).isEqualTo(150000L);
        assertThat(rows.get(0).orderCount()).isEqualTo(2);
        assertThat(rows.get(1).date()).isEqualTo("2026-10-21");
        assertThat(rows.get(1).revenue()).isEqualTo(70000L);
        assertThat(rows.get(1).orderCount()).isEqualTo(1);
    }

    @Test
    void topProductsAggregateByProductExcludeNullProductAndPendingOrders() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "rev-top");
        var buyerId = mirroredUser();

        var shirt = products.save(Product.create(shopId, null, "Ao Thun", "rev-top-shirt", "", ownerId)).getId();
        var jeans = products.save(Product.create(shopId, null, "Quan Jean", "rev-top-jeans", "", ownerId)).getId();

        var orderA = deliveredOrder(shopId, buyerId, 250000L, "ORD-TOP01");
        items.save(OrderItem.snapshot(orderA, shirt, null, "Ao Thun", "M / Den", "REV-SKU1", 100000L, 2));
        items.save(OrderItem.snapshot(orderA, jeans, null, "Quan Jean", "L / Xanh", "REV-SKU2", 50000L, 1));

        var orderB = deliveredOrder(shopId, buyerId, 100000L, "ORD-TOP02");
        items.save(OrderItem.snapshot(orderB, shirt, null, "Ao Thun", "M / Den", "REV-SKU1", 100000L, 1));
        items.save(OrderItem.snapshot(orderB, null, null, "Phu kien", "Free", "REV-SKU3", 10000L, 3));

        var orderC = Order.create("ORD-TOP03", shopId, buyerId, 1000000L, 0L, "An", "0901", "Ha Noi");
        orders.save(orderC);
        items.save(OrderItem.snapshot(orderC.getId(), shirt, null, "Ao Thun", "M / Den", "REV-SKU1", 100000L, 10));

        var rows = revenue.getTopProducts(ownerId, shopId, 5);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).productId()).isEqualTo(shirt);
        assertThat(rows.get(0).productName()).isEqualTo("Ao Thun");
        assertThat(rows.get(0).soldQuantity()).isEqualTo(3);
        assertThat(rows.get(0).totalRevenue()).isEqualTo(300000L);
        assertThat(rows.get(1).productId()).isEqualTo(jeans);
        assertThat(rows.get(1).soldQuantity()).isEqualTo(1);
        assertThat(rows.get(1).totalRevenue()).isEqualTo(50000L);
    }

    @Test
    void topProductsClampsLimitToAtLeastOne() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "rev-clamp");
        var buyerId = mirroredUser();

        var shirt = products.save(Product.create(shopId, null, "Ao Thun", "rev-clamp-shirt", "", ownerId)).getId();
        var jeans = products.save(Product.create(shopId, null, "Quan Jean", "rev-clamp-jeans", "", ownerId)).getId();

        var order = deliveredOrder(shopId, buyerId, 250000L, "ORD-CLAMP01");
        items.save(OrderItem.snapshot(order, jeans, null, "Quan Jean", "L / Xanh", "REV-SKU4", 50000L, 1));
        items.save(OrderItem.snapshot(order, shirt, null, "Ao Thun", "M / Den", "REV-SKU5", 100000L, 2));

        var rows = revenue.getTopProducts(ownerId, shopId, 0);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).productId()).isEqualTo(shirt);
        assertThat(rows.get(0).totalRevenue()).isEqualTo(200000L);
    }

    @Test
    void summaryCountsOnlyDeliveredOrdersAndAveragesByLongDivision() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "rev-summary");
        var buyerId = mirroredUser();

        deliveredOrder(shopId, buyerId, 100000L, "ORD-SUM01");
        deliveredOrder(shopId, buyerId, 50000L, "ORD-SUM02");
        var pending = Order.create("ORD-SUM03", shopId, buyerId, 999999L, 0L, "An", "0901", "Ha Noi");
        orders.save(pending);

        var summary = revenue.getSummary(ownerId, shopId);

        assertThat(summary.totalRevenue()).isEqualTo(150000L);
        assertThat(summary.totalOrders()).isEqualTo(2);
        assertThat(summary.avgOrderValue()).isEqualTo(75000L);
        assertThat(summary.walletBalance()).isEqualTo(0L);
        assertThat(summary.holdingBalance()).isEqualTo(0L);
    }

    @Test
    void summaryOfShopWithoutDeliveredOrdersIsAllZero() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "rev-empty");
        var buyerId = mirroredUser();

        var pending = Order.create("ORD-EMPTY01", shopId, buyerId, 999999L, 0L, "An", "0901", "Ha Noi");
        orders.save(pending);

        var summary = revenue.getSummary(ownerId, shopId);

        assertThat(summary.totalRevenue()).isEqualTo(0L);
        assertThat(summary.totalOrders()).isEqualTo(0L);
        assertThat(summary.avgOrderValue()).isEqualTo(0L);
    }
}
