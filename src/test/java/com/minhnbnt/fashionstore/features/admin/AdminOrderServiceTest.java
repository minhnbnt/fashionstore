package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AdminCancelReason;
import com.minhnbnt.fashionstore.model.OrderAlert;
import com.minhnbnt.fashionstore.model.OrderStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminOrderServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private AdminOrderService adminOrders;

    @Autowired
    private OrderService orders;

    @Autowired
    private ReturnRequestService returns;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

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

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, int stock) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            new CreateProductRequest(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, stock, true))
            )
        );
        return created.variants().get(0).id();
    }

    private OrderResponse placeOrder(UUID buyerId, UUID shopId, UUID variantId, int quantity) {
        var request = new CheckoutRequest(
            shopId, List.of(new CheckoutItemRequest(variantId, quantity)), "Nguyen An", "0901234567", "Ha Noi", 0L
        );
        return orders.checkout(buyerId, request);
    }

    private int stockOf(UUID variantId) {
        return jdbc.sql("SELECT stock_quantity FROM product_variant WHERE id = :id")
            .param("id", variantId)
            .query(Integer.class)
            .single();
    }

    /** Lùi mốc thời gian của đơn để giả lập đơn để lâu, không cần chờ thật. */
    private void ageOrder(UUID orderId, String column, String interval) {
        jdbc.sql("UPDATE orders SET " + column + " = now() - CAST(:interval AS interval) WHERE id = :id")
            .param("interval", interval)
            .param("id", orderId)
            .update();
    }

    private static AdminOrderFilter byShop(UUID shopId) {
        return new AdminOrderFilter(null, null, shopId, null, null, null, null);
    }

    private static AdminOrderFilter byShopAndAlert(UUID shopId, OrderAlert alert) {
        return new AdminOrderFilter(null, alert, shopId, null, null, null, null);
    }

    private static AdminOrderFilter byQuery(String query) {
        return new AdminOrderFilter(null, null, null, null, query, null, null);
    }

    private static AdminOrderFilter byShopAndDates(UUID shopId, @Nullable LocalDate from, @Nullable LocalDate to) {
        return new AdminOrderFilter(null, null, shopId, null, null, from, to);
    }

    private AdminOrderResponse onlyOrderOf(AdminOrderFilter filter) {
        var page = adminOrders.search(filter, 0, 20);
        assertThat(page.items()).hasSize(1);
        return page.items().get(0);
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void searchFindsOrderByCodeWithShopAndBuyer() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-search-code");
        var variantId = variantInShop(ownerId, shopId, "mon-search-code-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 2);

        var found = onlyOrderOf(byQuery(order.code()));

        assertThat(found.id()).isEqualTo(order.id());
        assertThat(found.shopName()).isEqualTo("Shop mon-search-code");
        assertThat(found.buyerEmail()).isEqualTo("u-" + buyerId + "@t.local");
        assertThat(found.itemCount()).isEqualTo(2);
        assertThat(found.firstItemName()).isEqualTo("Ao mon-search-code-ao");
        assertThat(found.total()).isEqualTo(200000L);
        assertThat(found.alert()).isNull();
    }

    @Test
    void searchMatchesBuyerEmail() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-search-email");
        var variantId = variantInShop(ownerId, shopId, "mon-search-email-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 1);

        var found = onlyOrderOf(byQuery(buyerId.toString()));

        assertThat(found.id()).isEqualTo(order.id());
    }

    @Test
    void pendingOrderOlderThanOneDayIsConfirmLate() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-confirm-late");
        var variantId = variantInShop(ownerId, shopId, "mon-confirm-late-ao", 10);
        var late = placeOrder(buyerId, shopId, variantId, 1);
        placeOrder(buyerId, shopId, variantId, 1);
        ageOrder(late.id(), "created_at", "25 hours");

        var found = onlyOrderOf(byShopAndAlert(shopId, OrderAlert.CONFIRM_LATE));

        assertThat(found.id()).isEqualTo(late.id());
        assertThat(found.alert()).isEqualTo("CONFIRM_LATE");
    }

    @Test
    void confirmedOrderNotHandedOverAfterTwoDaysIsHandoverLate() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-handover-late");
        var variantId = variantInShop(ownerId, shopId, "mon-handover-late-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 1);
        orders.confirm(ownerId, order.id());
        ageOrder(order.id(), "created_at", "49 hours");

        var found = onlyOrderOf(byShopAndAlert(shopId, OrderAlert.HANDOVER_LATE));

        assertThat(found.id()).isEqualTo(order.id());
        assertThat(found.alert()).isEqualTo("HANDOVER_LATE");
    }

    @Test
    void shippingOrderOlderThanSevenDaysIsDeliveryLate() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-delivery-late");
        var variantId = variantInShop(ownerId, shopId, "mon-delivery-late-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 1);
        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id());
        ageOrder(order.id(), "shipped_at", "8 days");

        var found = onlyOrderOf(byShopAndAlert(shopId, OrderAlert.DELIVERY_LATE));

        assertThat(found.alert()).isEqualTo("DELIVERY_LATE");
    }

    @Test
    void deliveredOrderWithPendingReturnIsReturnOpen() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-return-open");
        var variantId = variantInShop(ownerId, shopId, "mon-return-open-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 1);
        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id());
        orders.deliver(ownerId, order.id());
        returns.create(buyerId, order.id(), new CreateReturnRequest("DAMAGED", "Rach o tay", null));

        var found = onlyOrderOf(byShopAndAlert(shopId, OrderAlert.RETURN_OPEN));

        assertThat(found.alert()).isEqualTo("RETURN_OPEN");
        assertThat(found.returnStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void summaryCountsStatusesAndAlertsOfFilter() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-summary");
        var variantId = variantInShop(ownerId, shopId, "mon-summary-ao", 10);
        var late = placeOrder(buyerId, shopId, variantId, 1);
        placeOrder(buyerId, shopId, variantId, 1);
        var confirmed = placeOrder(buyerId, shopId, variantId, 1);
        orders.confirm(ownerId, confirmed.id());
        var cancelled = placeOrder(buyerId, shopId, variantId, 1);
        orders.cancel(buyerId, cancelled.id());
        ageOrder(late.id(), "created_at", "30 hours");

        var summary = adminOrders.summary(byShop(shopId));

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.pending()).isEqualTo(2);
        assertThat(summary.confirmed()).isEqualTo(1);
        assertThat(summary.shipping()).isZero();
        assertThat(summary.delivered()).isZero();
        assertThat(summary.cancelled()).isEqualTo(1);
        assertThat(summary.confirmLate()).isEqualTo(1);
        assertThat(summary.handoverLate()).isZero();
        assertThat(summary.deliveryLate()).isZero();
        assertThat(summary.returnOpen()).isZero();
    }

    @Test
    void dateRangeUsesVietnamDaysInclusive() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-date-range");
        var variantId = variantInShop(ownerId, shopId, "mon-date-range-ao", 10);
        var old = placeOrder(buyerId, shopId, variantId, 1);
        var fresh = placeOrder(buyerId, shopId, variantId, 1);
        ageOrder(old.id(), "created_at", "3 days");
        var today = LocalDate.now(ZONE);

        var todayOnly = onlyOrderOf(byShopAndDates(shopId, today, today));
        var page = adminOrders.search(byShopAndDates(shopId, today.minusDays(3), null), 0, 20);

        assertThat(todayOnly.id()).isEqualTo(fresh.id());
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void dateRangeFromAfterToIsBadRequest() {
        var today = LocalDate.now(ZONE);
        var filter = new AdminOrderFilter(null, null, null, null, null, today, today.minusDays(1));

        assertThatThrownBy(() -> adminOrders.search(filter, 0, 20))
            .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void detailShowsItemsTimelineAndReturns() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-detail");
        var variantId = variantInShop(ownerId, shopId, "mon-detail-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 2);
        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id());
        orders.deliver(ownerId, order.id());
        returns.create(buyerId, order.id(), new CreateReturnRequest("WRONG_ITEM", null, null));

        var detail = adminOrders.getDetail(order.id());

        assertThat(detail.order().code()).isEqualTo(order.code());
        assertThat(detail.shopStatus()).isEqualTo("APPROVED");
        assertThat(detail.shippingAddress()).isEqualTo("Ha Noi");
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().get(0).quantity()).isEqualTo(2);
        assertThat(detail.confirmedAt()).isNotNull();
        assertThat(detail.deliveredAt()).isNotNull();
        assertThat(detail.timeline())
            .extracting(entry -> entry.toStatus() + ":" + entry.actor())
            .containsExactly("PENDING:BUYER", "CONFIRMED:SELLER", "SHIPPING:SELLER", "DELIVERED:SELLER");
        assertThat(detail.returns()).hasSize(1);
        assertThat(detail.returns().get(0).reason()).isEqualTo("WRONG_ITEM");
    }

    @Test
    void detailOfUnknownOrderIsNotFound() {
        assertThatThrownBy(() -> adminOrders.getDetail(UUID.randomUUID()))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void adminCancelsPendingOrderWithReason() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-cancel-pending");
        var variantId = variantInShop(ownerId, shopId, "mon-cancel-pending-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 1);

        var cancelled = adminOrders.cancel(
            adminId, order.id(), AdminCancelReason.FRAUD_SUSPECTED, "Dat 20 don ao cung SDT"
        );

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledBy()).isEqualTo("ADMIN");
        assertThat(cancelled.cancelReason()).isEqualTo("FRAUD_SUSPECTED");

        var detail = adminOrders.getDetail(order.id());
        var last = detail.timeline().get(detail.timeline().size() - 1);
        assertThat(detail.cancelNote()).isEqualTo("Dat 20 don ao cung SDT");
        assertThat(last.toStatus()).isEqualTo("CANCELLED");
        assertThat(last.actor()).isEqualTo("ADMIN");
        assertThat(last.changedBy()).isEqualTo(adminId);
        assertThat(last.note()).isEqualTo("Dat 20 don ao cung SDT");
    }

    @Test
    void adminCancelOfConfirmedOrderRestocks() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-cancel-restock");
        var variantId = variantInShop(ownerId, shopId, "mon-cancel-restock-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 3);
        orders.confirm(ownerId, order.id());
        assertThat(stockOf(variantId)).isEqualTo(7);

        adminOrders.cancel(adminId, order.id(), AdminCancelReason.SELLER_UNRESPONSIVE, "Shop khong giao hang");

        assertThat(stockOf(variantId)).isEqualTo(10);
    }

    @Test
    void adminCannotCancelShippingOrder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-cancel-shipping");
        var variantId = variantInShop(ownerId, shopId, "mon-cancel-shipping-ao", 10);
        var order = placeOrder(buyerId, shopId, variantId, 1);
        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id());

        assertThatThrownBy(() -> adminOrders.cancel(adminId, order.id(), AdminCancelReason.OTHER, "Thu huy don"))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        assertThat(adminOrders.getDetail(order.id()).order().status()).isEqualTo(OrderStatus.SHIPPING.name());
    }

    @Test
    void adminCancelOfUnknownOrderIsNotFound() {
        var adminId = mirroredUser();

        assertThatThrownBy(() -> adminOrders.cancel(adminId, UUID.randomUUID(), AdminCancelReason.OTHER, "Khong co"))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void listShowsWhoCancelled() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "mon-cancelled-by");
        var variantId = variantInShop(ownerId, shopId, "mon-cancelled-by-ao", 10);
        var byBuyer = placeOrder(buyerId, shopId, variantId, 1);
        var bySeller = placeOrder(buyerId, shopId, variantId, 1);
        orders.cancel(buyerId, byBuyer.id());
        orders.sellerCancel(ownerId, bySeller.id(), new CancelOrderRequest("OUT_OF_STOCK", null));

        var page = adminOrders.search(byShop(shopId), 0, 20);

        assertThat(page.items())
            .extracting(order -> order.code() + ":" + order.cancelledBy())
            .containsExactlyInAnyOrder(byBuyer.code() + ":BUYER", bySeller.code() + ":SELLER");
    }
}
