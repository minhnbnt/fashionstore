package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.payment.PaymentFlowService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderItem;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RevenueServiceIntegrationTest {

    @Autowired

    private UploadRepository uploads;


    @Autowired
    private RevenueService revenue;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentFlowService paymentFlow;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private SellerSettlementRepository settlements;

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
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U"));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest(uploads, "Shop " + slug, slug));
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

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, String sku, long price, int stock) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Den", price, null, stock, true))
            )
        );
        return catalog.getDetail(created.id()).variants().getFirst().id();
    }

    @Test
    void settlementStaysHoldingAfterDeliverAndReleasesWith5PercentFeeOnlyWhenBuyerConfirmsReceipt() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "rev-escrow-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-escrow-" + suffix, "SKU-ESC-" + suffix, 200_000L, 10);

        var created = orderService.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 1)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.COD
            )
        );
        orderService.confirm(ownerId, created.id());
        orderService.ship(ownerId, created.id());
        orderService.deliver(ownerId, created.id());

        var afterDeliverSummary = revenue.getSummary(ownerId, shopId);
        assertThat(afterDeliverSummary.holdingBalance()).isEqualTo(190_000L);
        assertThat(afterDeliverSummary.walletBalance()).isEqualTo(0L);

        var settlementBeforeReceipt = settlements.findByOrderId(created.id()).orElseThrow();
        assertThat(settlementBeforeReceipt.getGrossAmount()).isEqualTo(200_000L);
        assertThat(settlementBeforeReceipt.getPlatformFee()).isEqualTo(10_000L);
        assertThat(settlementBeforeReceipt.getNetAmount()).isEqualTo(190_000L);
        assertThat(settlementBeforeReceipt.getStatus()).isEqualTo(SettlementStatus.HOLDING);

        orderService.confirmReceipt(buyerId, created.id());

        var afterReceiptSummary = revenue.getSummary(ownerId, shopId);
        assertThat(afterReceiptSummary.holdingBalance()).isEqualTo(0L);
        assertThat(afterReceiptSummary.walletBalance()).isEqualTo(190_000L);

        var settlementAfterReceipt = settlements.findByOrderId(created.id()).orElseThrow();
        assertThat(settlementAfterReceipt.getStatus()).isEqualTo(SettlementStatus.RELEASED);
        assertThat(settlementAfterReceipt.getReleasedAt()).isNotNull();
    }

    @Test
    void deliverOnPaidVietQrOrderPreservesPaidStatus() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "rev-vqr-del-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-vqr-del-" + suffix, "SKU-VQRD-" + suffix, 200_000L, 10);

        var created = orderService.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 1)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.VIETQR
            )
        );
        var txnRef = Objects.requireNonNull(created.paymentTxnRef());
        paymentFlow.processIncomingTransfer("CK " + txnRef, created.total(), "FT-VQRD", "{}");

        orderService.confirm(ownerId, created.id());
        orderService.ship(ownerId, created.id());
        var delivered = orderService.deliver(ownerId, created.id());

        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.paymentStatus()).isEqualTo("PAID");

        var summaryWhileDelivered = revenue.getSummary(ownerId, shopId);
        assertThat(summaryWhileDelivered.holdingBalance()).isEqualTo(190_000L);
        assertThat(summaryWhileDelivered.walletBalance()).isEqualTo(0L);

        orderService.confirmReceipt(buyerId, created.id());
        var summaryAfterReceipt = revenue.getSummary(ownerId, shopId);
        assertThat(summaryAfterReceipt.holdingBalance()).isEqualTo(0L);
        assertThat(summaryAfterReceipt.walletBalance()).isEqualTo(190_000L);
    }

    @Test
    void creatingWithdrawalImmediatelyDeductsAvailableWalletBalanceAndRejectingRestoresIt() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "rev-withdraw-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-withdraw-" + suffix, "SKU-WD-" + suffix, 200_000L, 10);

        var created = orderService.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 1)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.COD
            )
        );
        orderService.confirm(ownerId, created.id());
        orderService.ship(ownerId, created.id());
        orderService.deliver(ownerId, created.id());
        orderService.confirmReceipt(buyerId, created.id());

        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isEqualTo(190_000L);

        var w1 = revenue.createWithdrawal(
            ownerId,
            new CreateWithdrawalRequest(shopId, 100_000L, "Vietcombank", "999888777", "NGUYEN VAN AN")
        );
        assertThat(w1.status()).isEqualTo("REQUESTED");
        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isEqualTo(90_000L);

        assertThatThrownBy(() -> revenue.createWithdrawal(
            ownerId,
            new CreateWithdrawalRequest(shopId, 100_000L, "Vietcombank", "999888777", "NGUYEN VAN AN")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        var adminPage = revenue.listAdminWithdrawals("REQUESTED", 0, 20);
        assertThat(adminPage.items()).extracting(w -> w.id()).contains(w1.id());

        var rejected = revenue.rejectWithdrawal(adminId, w1.id());
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isEqualTo(190_000L);

        var w2 = revenue.createWithdrawal(
            ownerId,
            new CreateWithdrawalRequest(shopId, 100_000L, "Vietcombank", "999888777", "NGUYEN VAN AN")
        );
        var approved = revenue.approveWithdrawal(adminId, w2.id());
        assertThat(approved.status()).isEqualTo("COMPLETED");
        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isEqualTo(90_000L);

        assertThatThrownBy(() -> revenue.approveWithdrawal(adminId, w2.id()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void concurrentCreateWithdrawalCannotExceedAvailableBalance() throws Exception {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "rev-conc-wd-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-conc-wd-" + suffix, "SKU-CWD-" + suffix, 200_000L, 10);

        var created = orderService.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 1)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.COD
            )
        );
        orderService.confirm(ownerId, created.id());
        orderService.ship(ownerId, created.id());
        orderService.deliver(ownerId, created.id());
        orderService.confirmReceipt(buyerId, created.id());

        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isEqualTo(190_000L);

        var readyLatch = new CountDownLatch(2);
        var startLatch = new CountDownLatch(1);
        var successCount = new AtomicInteger();
        var badRequestCount = new AtomicInteger();
        var unexpectedErrors = new CopyOnWriteArrayList<Throwable>();

        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> task = () -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    revenue.createWithdrawal(
                        ownerId,
                        new CreateWithdrawalRequest(shopId, 150_000L, "Vietcombank", "999888777", "NGUYEN VAN AN")
                    );
                    successCount.incrementAndGet();
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        badRequestCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                }
                return null;
            };

            var f1 = pool.submit(task);
            var f2 = pool.submit(task);
            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            f1.get(15, TimeUnit.SECONDS);
            f2.get(15, TimeUnit.SECONDS);
        }

        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(badRequestCount.get()).isEqualTo(1);
        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isEqualTo(40_000L);
    }

    @Test
    void totalRevenueAndDailyRevenueSubtractVoucherDiscountAndCompletedReturnRefunds() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "rev-net-" + UUID.randomUUID().toString().substring(0, 8));
        var buyerId = mirroredUser();

        // Order 1: subtotal 200,000, voucher discount 30,000, partial completed refund 50,000 -> net 120,000
        var order1 = deliveredOrder(shopId, buyerId, 200_000L, "ORD-NET01");
        jdbc.sql("UPDATE orders SET discount_amount = 30000, total = 170000 WHERE id = :id")
            .param("id", order1)
            .update();
        insertReturnRequest(order1, shopId, buyerId, "COMPLETED", "REFUND", 50_000L);

        // Order 2: subtotal 100,000, discount 10,000, completed EXCHANGE return -> net 90,000
        var order2 = deliveredOrder(shopId, buyerId, 100_000L, "ORD-NET02");
        jdbc.sql("UPDATE orders SET discount_amount = 10000, total = 90000 WHERE id = :id")
            .param("id", order2)
            .update();
        insertReturnRequest(order2, shopId, buyerId, "COMPLETED", "EXCHANGE", 90_000L);

        setCreatedAt(order1, "2026-10-25T10:00:00Z");
        setCreatedAt(order2, "2026-10-25T12:00:00Z");

        var summary = revenue.getSummary(ownerId, shopId);
        assertThat(summary.totalRevenue()).isEqualTo(210_000L);
        assertThat(summary.totalOrders()).isEqualTo(2);
        assertThat(summary.avgOrderValue()).isEqualTo(105_000L);

        var daily = revenue.getDailyRevenue(ownerId, shopId);
        assertThat(daily).hasSize(1);
        assertThat(daily.getFirst().date()).isEqualTo("2026-10-25");
        assertThat(daily.getFirst().revenue()).isEqualTo(210_000L);
        assertThat(daily.getFirst().orderCount()).isEqualTo(2);
    }

    private void insertReturnRequest(
        UUID orderId,
        UUID shopId,
        UUID buyerId,
        String status,
        String resolution,
        long refundAmount
    ) {
        var values = new LinkedHashMap<String, Object>();
        values.put("order_id", orderId);
        values.put("status", status);
        values.put("resolution", resolution);
        values.put("refund_amount", refundAmount);

        var requiredCols = jdbc.sql("""
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'return_request'
              AND is_nullable = 'NO'
              AND column_default IS NULL
            """)
            .query((rs, rowNum) -> Map.entry(rs.getString("column_name"), rs.getString("data_type")))
            .list();

        for (var col : requiredCols) {
            var name = col.getKey();
            var type = col.getValue();
            if (values.containsKey(name)) {
                continue;
            }
            if ("id".equals(name)) {
                values.put(name, UUID.randomUUID());
            } else if ("shop_id".equals(name)) {
                values.put(name, shopId);
            } else if ("buyer_id".equals(name) || "user_id".equals(name) || "requester_id".equals(name)) {
                values.put(name, buyerId);
            } else if (type.contains("uuid")) {
                values.put(name, UUID.randomUUID());
            } else if (type.contains("char") || type.contains("text")) {
                values.put(name, "Defective item");
            } else if (type.contains("int") || type.contains("numeric")) {
                values.put(name, 0L);
            } else if (type.contains("bool")) {
                values.put(name, false);
            } else if (type.contains("timestamp")) {
                values.put(name, OffsetDateTime.now(ZoneOffset.UTC));
            }
        }

        var colNames = String.join(", ", values.keySet());
        var paramPlaceholders = values.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        var spec = jdbc.sql("INSERT INTO return_request (" + colNames + ") VALUES (" + paramPlaceholders + ")");
        for (var entry : values.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        spec.update();
    }
}
