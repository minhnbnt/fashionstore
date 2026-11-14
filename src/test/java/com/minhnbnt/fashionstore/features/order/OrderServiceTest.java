package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.AddOrderTrackingEventRequest;
import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.BuyerCancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.dto.order.ShipOrderRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductImageRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ProductImage;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private ReturnRequestService returnRequests;

    @Autowired
    private ProductImageRepository productImages;

    @Autowired
    private com.minhnbnt.fashionstore.features.notification.NotificationRepository notifications;

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U"));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, String sku, int stock) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Den", 100000L, null, stock, true))
            )
        );
        var detail = catalog.getDetail(created.id());
        return detail.variants().get(0).id();
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void checkoutCreatesPendingOrder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-a");
        var variantId = variantInShop(ownerId, shopId, "ao-order-a", "SKU-ORDER-A", 10);

        var response = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 2)), "An", "0901", "Ha Noi", 0L)
        );

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.total()).isEqualTo(200000L);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void checkoutAcrossShopsFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopA = approvedShop(ownerId, "shop-order-b1");
        var shopB = approvedShop(ownerId, "shop-order-b2");
        var variantB = variantInShop(ownerId, shopB, "ao-order-b", "SKU-ORDER-B", 10);

        assertThatThrownBy(() -> orders.checkout(
            buyerId,
            new CheckoutRequest(shopA, List.of(new CheckoutItemRequest(variantB, 1)), "An", "0901", "Ha Noi", 0L)
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void buyerCannotSeeOthersOrder() {
        var ownerId = mirroredUser();
        var buyerA = mirroredUser();
        var buyerB = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-c");
        var variantId = variantInShop(ownerId, shopId, "ao-order-c", "SKU-ORDER-C", 10);
        var created = orders.checkout(
            buyerA,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        assertThatThrownBy(() -> orders.getDetail(buyerB, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmDecrementsStockAtomically() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-d");
        var variantId = variantInShop(ownerId, shopId, "ao-order-d", "SKU-ORDER-D", 5);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 3)), "An", "0901", "Ha Noi", 0L)
        );

        var confirmed = orders.confirm(ownerId, created.id());

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        var detail = catalog.getDetail(Objects.requireNonNull(created.items().get(0).productId()));
        var remaining = detail.variants().stream()
            .filter(v -> v.id().equals(variantId))
            .findFirst()
            .orElseThrow()
            .stockQuantity();
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void sellerCancelPendingOrderRestocks() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-sc");
        var variantId = variantInShop(ownerId, shopId, "ao-order-sc", "SKU-ORDER-SC", 10);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 2)), "An", "0901", "Ha Noi", 0L)
        );
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(8);

        orders.sellerCancel(ownerId, created.id(), new CancelOrderRequest("Out of stock", ""));
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void buyerCancelsPendingOrder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-f");
        var variantId = variantInShop(ownerId, shopId, "ao-order-f", "SKU-ORDER-F", 10);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        var cancelled = orders.cancel(buyerId, created.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelAfterConfirmFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-g");
        var variantId = variantInShop(ownerId, shopId, "ao-order-g", "SKU-ORDER-G", 10);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );
        orders.confirm(ownerId, created.id());

        assertThatThrownBy(() -> orders.cancel(buyerId, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void fullLifecycleToDeliveredCollectsCod() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-h");
        var variantId = variantInShop(ownerId, shopId, "ao-order-h", "SKU-ORDER-H", 10);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 5000L)
        );

        assertThat(created.total()).isEqualTo(105000L);

        var shipped = orders.ship(ownerId, orders.confirm(ownerId, created.id()).id());

        assertThat(shipped.status()).isEqualTo("SHIPPING");

        var delivered = orders.deliver(ownerId, created.id());

        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.paymentStatus()).isEqualTo("COLLECTED");
    }

    @Test
    void sellerListOnlyShowsOwnShops() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var strangerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-i");
        var variantId = variantInShop(ownerId, shopId, "ao-order-i", "SKU-ORDER-I", 10);
        orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        assertThat(orders.listSellerOrders(ownerId)).hasSize(1);
        assertThat(orders.listSellerOrders(strangerId)).isEmpty();
    }

    @Test
    void strangerCannotConfirmOrder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var strangerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-j");
        var variantId = variantInShop(ownerId, shopId, "ao-order-j", "SKU-ORDER-J", 10);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        assertThatThrownBy(() -> orders.confirm(strangerId, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));
    }

    @Test
    void confirmRejectsExpiredPendingOrderAndCancelsBySystem() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-expired-confirm");
        var variantId = variantInShop(ownerId, shopId, "ao-expired", "SKU-EXPIRED", 10);
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        jdbc.sql("UPDATE orders SET created_at = NOW() - INTERVAL '25 hours' WHERE id = ?")
            .param(created.id())
            .update();

        assertThatThrownBy(() -> orders.confirm(ownerId, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        var cancelled = orders.getDetail(buyerId, created.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledBy()).isEqualTo("SYSTEM");
        assertThat(cancelled.cancelReason()).contains("24 giờ");
    }

    @Test
    void checkoutNotifiesShopMembers() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-notif-1");
        var variantId = variantInShop(ownerId, shopId, "ao-notif-1", "SKU-NOTIF-1", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        var ownerPage = notifications.findPage(ownerId, null, 0, 10);
        assertThat(ownerPage).hasSize(1);
        var notif = ownerPage.getFirst();
        assertThat(notif.category()).isEqualTo("SELLING");
        assertThat(notif.type()).isEqualTo("ORDER_PLACED");
        assertThat(notif.title()).isEqualTo("Có đơn hàng mới #" + created.code());
        assertThat(notif.body()).isEqualTo("Khách hàng vừa đặt đơn #" + created.code() + " tại shop của bạn.");
        assertThat(notif.link()).isEqualTo("/shop?shopId=" + shopId + "&tab=orders&orderId=" + created.id());

        var buyerPage = notifications.findPage(buyerId, null, 0, 10);
        assertThat(buyerPage).isEmpty();
    }

    @Test
    void confirmShipDeliverNotifyBuyer() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-notif-2");
        var variantId = variantInShop(ownerId, shopId, "ao-notif-2", "SKU-NOTIF-2", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        // Confirm
        orders.confirm(ownerId, created.id());
        var buyerPage1 = notifications.findPage(buyerId, null, 0, 10);
        assertThat(buyerPage1).hasSize(1);
        assertThat(buyerPage1.getFirst().category()).isEqualTo("ORDER");
        assertThat(buyerPage1.getFirst().type()).isEqualTo("ORDER_CONFIRMED");
        assertThat(buyerPage1.getFirst().title()).isEqualTo("Đơn #" + created.code() + " đã được xác nhận");
        assertThat(buyerPage1.getFirst().link()).isEqualTo("/account/orders/" + created.id());

        // Ship
        orders.ship(ownerId, created.id());
        var buyerPage2 = notifications.findPage(buyerId, null, 0, 10);
        assertThat(buyerPage2).hasSize(2);
        assertThat(buyerPage2.getFirst().category()).isEqualTo("ORDER");
        assertThat(buyerPage2.getFirst().type()).isEqualTo("ORDER_SHIPPED");
        assertThat(buyerPage2.getFirst().title()).isEqualTo("Đơn #" + created.code() + " đang được giao");
        assertThat(buyerPage2.getFirst().link()).isEqualTo("/account/orders/" + created.id());

        // Deliver
        orders.deliver(ownerId, created.id());
        var buyerPage3 = notifications.findPage(buyerId, null, 0, 10);
        assertThat(buyerPage3).hasSize(3);
        assertThat(buyerPage3.getFirst().category()).isEqualTo("ORDER");
        assertThat(buyerPage3.getFirst().type()).isEqualTo("ORDER_DELIVERED");
        assertThat(buyerPage3.getFirst().title()).isEqualTo("Đơn #" + created.code() + " đã được giao");
        assertThat(buyerPage3.getFirst().link()).isEqualTo("/account/orders/" + created.id());
    }

    @Test
    void buyerCancelNotifiesShopMembers() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-notif-3");
        var variantId = variantInShop(ownerId, shopId, "ao-notif-3", "SKU-NOTIF-3", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        orders.cancel(buyerId, created.id());

        var ownerPage = notifications.findPage(ownerId, null, 0, 10);
        assertThat(ownerPage).hasSize(2);
        var cancelNotif = ownerPage.getFirst();
        assertThat(cancelNotif.category()).isEqualTo("SELLING");
        assertThat(cancelNotif.type()).isEqualTo("ORDER_CANCELLED");
        assertThat(cancelNotif.title()).isEqualTo("Đơn #" + created.code() + " đã bị hủy");
        assertThat(cancelNotif.body()).isEqualTo("Khách hàng đã hủy đơn #" + created.code() + ".");
        assertThat(cancelNotif.link()).isEqualTo("/shop?shopId=" + shopId + "&tab=orders&orderId=" + created.id());
    }

    @Test
    void sellerCancelNotifiesBuyer() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-notif-4");
        var variantId = variantInShop(ownerId, shopId, "ao-notif-4", "SKU-NOTIF-4", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );

        orders.sellerCancel(
            ownerId,
            created.id(),
            new com.minhnbnt.fashionstore.dto.order.CancelOrderRequest("Hết hàng", "Hết màu trắng")
        );

        var buyerPage = notifications.findPage(buyerId, null, 0, 10);
        assertThat(buyerPage).hasSize(1);
        var cancelNotif = buyerPage.getFirst();
        assertThat(cancelNotif.category()).isEqualTo("ORDER");
        assertThat(cancelNotif.type()).isEqualTo("ORDER_CANCELLED");
        assertThat(cancelNotif.title()).isEqualTo("Đơn #" + created.code() + " đã bị hủy");
        assertThat(cancelNotif.body()).isEqualTo("Đơn #" + created.code() + " đã bị hủy: Hết hàng");
        assertThat(cancelNotif.link()).isEqualTo("/account/orders/" + created.id());
    }

    @Test
    void checkoutFailureLeavesNoNotification() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var unapprovedShopId = UUID.randomUUID();

        assertThatThrownBy(() -> orders.checkout(
            buyerId,
            new CheckoutRequest(
                unapprovedShopId,
                List.of(new CheckoutItemRequest(UUID.randomUUID(), 1)),
                "An",
                "0901",
                "Ha Noi",
                0L
            )
        )).isInstanceOf(ResponseStatusException.class);

        assertThat(notifications.countByUser(ownerId)).isZero();
        assertThat(notifications.countByUser(buyerId)).isZero();
    }

    @Test
    void checkoutDeductsStockImmediately() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-deduct");
        var variantId = variantInShop(ownerId, shopId, "ao-order-deduct", "SKU-ORDER-DEDUCT", 10);

        orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 3)), "An", "0901", "Ha Noi", 0L)
        );

        var variant = catalog.findVariantById(variantId).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void checkoutFailsWhenStockInsufficient() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-insuf");
        var variantId = variantInShop(ownerId, shopId, "ao-order-insuf", "SKU-ORDER-INSUF", 2);

        assertThatThrownBy(() -> orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 5)), "An", "0901", "Ha Noi", 0L)
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void buyerCancelRestocksPendingOrder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-rc");
        var variantId = variantInShop(ownerId, shopId, "ao-order-rc", "SKU-ORDER-RC", 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 4)), "An", "0901", "Ha Noi", 0L)
        );
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(6);

        orders.cancel(buyerId, created.id());
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void enrichesOrderAndItemsWithVariantImagePriorityAndAvailability() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-enrich");
        var variantId = variantInShop(ownerId, shopId, "ao-order-enrich", "SKU-ORDER-ENR", 10);
        var variant = catalog.findVariantById(variantId).orElseThrow();
        productImages.save(ProductImage.create(variant.getProductId(), null, "products/generic.jpg", 0));
        productImages.save(ProductImage.create(variant.getProductId(), variantId, "products/variant.jpg", 1));

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );
        assertThat(created.shopName()).isEqualTo("Shop shop-order-enrich");
        assertThat(created.shopSlug()).isEqualTo("shop-order-enrich");
        assertThat(created.items().getFirst().imageKey()).isEqualTo("products/variant.jpg");
        assertThat(created.items().getFirst().productAvailable()).isTrue();

        catalog.updateProduct(
            ownerId,
            shopId,
            variant.getProductId(),
            new UpdateProductRequest(null, null, null, "HIDDEN", null)
        );
        var reloaded = orders.getMine(buyerId);
        assertThat(reloaded.getFirst().items().getFirst().productAvailable()).isFalse();
    }

    @Test
    void shipAndTrackingEventsRecordHistoryAuditAndLongAddress() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-track");
        var variantId = variantInShop(ownerId, shopId, "ao-order-track", "SKU-ORDER-TRK", 10);
        var longAddress = "So nha 123, " + "Duong Dai Lo Thang Long, ".repeat(12) + "Ha Noi";
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", longAddress, 0L)
        );

        orders.confirm(ownerId, created.id());
        var shipped = orders.ship(ownerId, created.id(), new ShipOrderRequest("  ", "GHTK-889"));
        assertThat(shipped.shippingCarrier()).isEqualTo("GHN");
        assertThat(shipped.trackingCode()).isEqualTo("GHTK-889");
        assertThat(shipped.shippedAt()).isNotNull();

        orders.addTrackingEvent(
            ownerId,
            created.id(),
            new AddOrderTrackingEventRequest("IN_TRANSIT", "Da roi kho Ha Noi", "Kho HN", "Xe tai X01")
        );
        var trackingList = orders.getBuyerTracking(buyerId, created.id());
        assertThat(trackingList).hasSize(2);
        assertThat(trackingList.getFirst().statusCode()).isEqualTo("IN_TRANSIT");
        assertThat(trackingList.get(1).statusCode()).isEqualTo("PICKED_UP");

        var delivered = orders.deliver(ownerId, created.id());
        assertThat(delivered.deliveredAt()).isNotNull();
        assertThat(delivered.autoCompleteAt()).isNotNull();
        assertThat(orders.getSellerTracking(ownerId, created.id())).hasSize(3);
        assertThat(orders.getSellerTracking(ownerId, created.id()).getFirst().location()).isEqualTo(longAddress);

        assertThatThrownBy(() -> orders.addTrackingEvent(
            ownerId,
            created.id(),
            new AddOrderTrackingEventRequest("CUSTOM", "Tre", null, null)
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void buyerCancelWithReasonsAndConfirmReceiptReturnLock() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-order-cr");
        var variantId = variantInShop(ownerId, shopId, "ao-order-cr", "SKU-ORDER-CR", 10);

        var toCancel = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );
        assertThatThrownBy(() -> orders.cancel(buyerId, toCancel.id(), new BuyerCancelOrderRequest("OTHER", "  ")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        var cancelled = orders.cancel(
            buyerId,
            toCancel.id(),
            new BuyerCancelOrderRequest("CHANGE_MIND", "Doi mau khac")
        );
        assertThat(cancelled.cancelReason()).isEqualTo("CHANGE_MIND");
        assertThat(cancelled.cancelNote()).isEqualTo("Doi mau khac");
        assertThat(orders.getBuyerTimeline(buyerId, toCancel.id())).hasSize(2);

        var order = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        );
        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id(), null);
        orders.deliver(ownerId, order.id());

        var ret = returnRequests.create(buyerId, order.id(), new CreateReturnRequest("DAMAGED", "Rach", List.of()));
        assertThat(orders.getDetail(buyerId, order.id()).autoCompleteAt()).isNull();
        assertThat(orders.getDetail(buyerId, order.id()).returnStatus()).isEqualTo("REQUESTED");
        assertThatThrownBy(() -> orders.confirmReceipt(buyerId, order.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        returnRequests.reject(ownerId, ret.id(), new RejectReturnRequest("Khong du dieu kien"));
        var received1 = orders.confirmReceipt(buyerId, order.id());
        var received2 = orders.confirmReceipt(buyerId, order.id());
        assertThat(received1.receivedAt()).isNotNull();
        assertThat(received2.receivedAt()).isEqualTo(received1.receivedAt());
        assertThat(received2.autoCompleteAt()).isNull();

        var timeline = orders.getBuyerTimeline(buyerId, order.id());
        long receiptCount = timeline.stream()
            .filter(t -> "DELIVERED".equals(t.fromStatus()) && "DELIVERED".equals(t.toStatus()))
            .count();
        assertThat(receiptCount).isEqualTo(1L);
    }
}

