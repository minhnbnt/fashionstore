package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.BuyerCancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.cart.CartService;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationRepository;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.PaymentExpiryJob;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "test.isolation=PaymentFlowServiceTest")
class PaymentFlowServiceTest {

    @Autowired
    private PaymentFlowService paymentFlow;

    @Autowired
    private PaymentExpiryJob paymentExpiryJob;

    @Autowired
    private CartService carts;

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private SellerSettlementRepository settlements;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private JdbcClient jdbc;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "User " + id));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest(uploads, "Shop " + slug, slug));
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
                List.of(new CreateVariantRequest(sku, "M", "Den", 100_000L, null, stock, true))
            )
        );
        var detail = catalog.getDetail(created.id());
        return detail.variants().getFirst().id();
    }

    @Test
    void exactTransferActivatesAllOrdersInSessionAndCreatesHoldingSettlements() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopA = approvedShop(ownerId, "flow-exact-a-" + suffix);
        var shopB = approvedShop(ownerId, "flow-exact-b-" + suffix);
        var variantA = variantInShop(ownerId, shopA, "ao-exact-a-" + suffix, "SKU-EX-A-" + suffix, 10);
        var variantB = variantInShop(ownerId, shopB, "ao-exact-b-" + suffix, "SKU-EX-B-" + suffix, 10);

        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(
            buyerId,
            new CartCheckoutRequest("An", "0901234567", "Ha Noi", null, PaymentMethod.VIETQR)
        );
        assertThat(placed).hasSize(2);
        var txnRef = Objects.requireNonNull(placed.getFirst().paymentTxnRef());

        var result = paymentFlow.processIncomingTransfer(
            "MBVCB.998877.chuyen tien " + txnRef.toLowerCase(Locale.ROOT) + " thanh toan",
            360_000L,
            "FT12345",
            "{\"id\":\"FT12345\"}"
        );

        assertThat(result.outcome()).isEqualTo(TransferOutcome.SUCCESS);
        assertThat(result.txnRef()).isEqualTo(txnRef);
        assertThat(result.expectedAmount()).isEqualTo(360_000L);
        assertThat(result.receivedAmount()).isEqualTo(360_000L);
        assertThat(result.affectedOrderIds()).hasSize(2);

        for (var placedOrder : placed) {
            var detail = orders.getDetail(buyerId, placedOrder.id());
            assertThat(detail.status()).isEqualTo("PENDING");
            assertThat(detail.paymentStatus()).isEqualTo("PAID");

            var settlement = settlements.findByOrderId(placedOrder.id()).orElseThrow();
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.HOLDING);
            assertThat(settlement.getGrossAmount()).isEqualTo(placedOrder.subtotal());
            assertThat(settlement.getNetAmount())
                .isEqualTo(settlement.getGrossAmount() - settlement.getPlatformFee());
        }

        assertThat(orders.listSellerOrders(ownerId)).hasSize(2);
        assertThat(orders.countByShop(shopA)).isEqualTo(1L);
        assertThat(orders.countByShop(shopB)).isEqualTo(1L);

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.status()).isEqualTo("SUCCESS");
        assertThat(session.receivedAmount()).isEqualTo(360_000L);
        assertThat(session.paidAt()).isNotNull();
    }

    @Test
    void underpaidTransferMarksTransactionUnderpaidForRefundAndKeepsOrdersAwaitingPayment() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopA = approvedShop(ownerId, "flow-under-a-" + suffix);
        var shopB = approvedShop(ownerId, "flow-under-b-" + suffix);
        var variantA = variantInShop(ownerId, shopA, "ao-under-a-" + suffix, "SKU-UN-A-" + suffix, 10);
        var variantB = variantInShop(ownerId, shopB, "ao-under-b-" + suffix, "SKU-UN-B-" + suffix, 10);

        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(
            buyerId,
            new CartCheckoutRequest("An", "0901234567", "Ha Noi", null, PaymentMethod.VIETQR)
        );
        var txnRef = Objects.requireNonNull(placed.getFirst().paymentTxnRef());

        var result = paymentFlow.processIncomingTransfer(
            "CK " + txnRef,
            150_000L,
            "FT-UNDER",
            "{\"id\":\"FT-UNDER\"}"
        );

        assertThat(result.outcome()).isEqualTo(TransferOutcome.UNDERPAID);
        assertThat(result.txnRef()).isEqualTo(txnRef);
        assertThat(result.expectedAmount()).isEqualTo(360_000L);
        assertThat(result.receivedAmount()).isEqualTo(150_000L);

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.status()).isEqualTo("UNDERPAID");
        assertThat(session.receivedAmount()).isEqualTo(150_000L);
        assertThat(session.refundStatus()).isEqualTo("PENDING");
        assertThat(session.refundAmount()).isEqualTo(150_000L);
        assertThat(session.refundReason()).contains("150000/360000");

        for (var placedOrder : placed) {
            var detail = orders.getDetail(buyerId, placedOrder.id());
            assertThat(detail.status()).isEqualTo("AWAITING_PAYMENT");
            assertThat(detail.paymentStatus()).isEqualTo("UNPAID");
            assertThat(settlements.findByOrderId(placedOrder.id())).isEmpty();
        }
        assertThat(orders.listSellerOrders(ownerId)).isEmpty();
    }

    @Test
    void duplicateWebhookCallIsIdempotent() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "flow-idem-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-idem-" + suffix, "SKU-IDEM-" + suffix, 10);

        var created = orders.checkout(
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

        var first = paymentFlow.processIncomingTransfer("CK " + txnRef, 130_000L, "FT-1", "{}");
        assertThat(first.outcome()).isEqualTo(TransferOutcome.SUCCESS);

        var timelineAfterFirst = orders.getBuyerTimeline(buyerId, created.id());

        var second = paymentFlow.processIncomingTransfer("CK " + txnRef, 130_000L, "FT-1", "{}");
        assertThat(second.outcome()).isEqualTo(TransferOutcome.ALREADY_PROCESSED);

        var timelineAfterSecond = orders.getBuyerTimeline(buyerId, created.id());
        assertThat(timelineAfterSecond).hasSameSizeAs(timelineAfterFirst);
    }

    @Test
    void lateTransferAfterOrderExpiredFlagsOrderAndTransactionForRefund() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "flow-late-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-late-" + suffix, "SKU-LATE-" + suffix, 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 2)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.VIETQR
            )
        );
        var txnRef = Objects.requireNonNull(created.paymentTxnRef());
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(8);

        jdbc.sql("UPDATE orders SET payment_expires_at = NOW() - INTERVAL '2 minutes' WHERE id = :id")
            .param("id", created.id())
            .update();

        paymentExpiryJob.expireOverduePayments();

        var afterExpiry = orders.getDetail(buyerId, created.id());
        assertThat(afterExpiry.status()).isEqualTo("CANCELLED");
        assertThat(afterExpiry.paymentStatus()).isEqualTo("FAILED");
        assertThat(afterExpiry.cancelReason()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);

        var lateResult = paymentFlow.processIncomingTransfer("CK " + txnRef, 230_000L, "FT-LATE", "{}");
        assertThat(lateResult.outcome()).isEqualTo(TransferOutcome.SUCCESS);

        var afterLatePayment = orders.getDetail(buyerId, created.id());
        assertThat(afterLatePayment.status()).isEqualTo("CANCELLED");
        assertThat(afterLatePayment.paymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.status()).isEqualTo("SUCCESS");
        assertThat(session.refundStatus()).isEqualTo("PENDING");
        assertThat(session.refundAmount()).isEqualTo(230_000L);
        assertThat(session.refundReason()).contains(created.code());
    }

    @Test
    void retryAfterUnderpaidCreatesFreshSessionForSingleOrder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopA = approvedShop(ownerId, "flow-retry-a-" + suffix);
        var shopB = approvedShop(ownerId, "flow-retry-b-" + suffix);
        var variantA = variantInShop(ownerId, shopA, "ao-retry-a-" + suffix, "SKU-RT-A-" + suffix, 10);
        var variantB = variantInShop(ownerId, shopB, "ao-retry-b-" + suffix, "SKU-RT-B-" + suffix, 10);

        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(
            buyerId,
            new CartCheckoutRequest("An", "0901234567", "Ha Noi", null, PaymentMethod.VIETQR)
        );
        var initialTxnRef = Objects.requireNonNull(placed.getFirst().paymentTxnRef());

        paymentFlow.processIncomingTransfer("CK " + initialTxnRef, 100_000L, "FT-PART", "{}");

        var firstOrder = placed.getFirst();
        var retriedSession = paymentFlow.retryForOrder(buyerId, firstOrder.id());

        assertThat(retriedSession.txnRef()).isNotEqualTo(initialTxnRef);
        assertThat(retriedSession.status()).isEqualTo("PENDING");
        assertThat(retriedSession.amount()).isEqualTo(firstOrder.total());
        assertThat(retriedSession.orders()).hasSize(1);
        assertThat(retriedSession.orders().getFirst().orderId()).isEqualTo(firstOrder.id());

        var repeatedRetry = paymentFlow.retryForOrder(buyerId, firstOrder.id());
        assertThat(repeatedRetry.txnRef()).isEqualTo(retriedSession.txnRef());
    }

    @Test
    void retryAfterExpiryCancelsAndReturns409() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "flow-retexp-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-retexp-" + suffix, "SKU-RE-" + suffix, 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 2)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.VIETQR
            )
        );
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(8);

        jdbc.sql("UPDATE orders SET payment_expires_at = NOW() - INTERVAL '1 minute' WHERE id = :id")
            .param("id", created.id())
            .update();

        assertThatThrownBy(() -> paymentFlow.retryForOrder(buyerId, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Payment time has expired");
            });

        var reloaded = orders.getDetail(buyerId, created.id());
        assertThat(reloaded.status()).isEqualTo("CANCELLED");
        assertThat(reloaded.paymentStatus()).isEqualTo("FAILED");
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void buyerCanCancelAwaitingPaymentOrderAndRestocksInventory() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "flow-bcancel-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-bcancel-" + suffix, "SKU-BC-" + suffix, 10);

        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 3)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.VIETQR
            )
        );
        var txnRef = Objects.requireNonNull(created.paymentTxnRef());
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);

        var cancelled = orders.cancel(
            buyerId,
            created.id(),
            new BuyerCancelOrderRequest("CHANGE_MIND", "Khong mua nua")
        );

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.paymentStatus()).isEqualTo("FAILED");
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(notifications.findPage(ownerId, null, 0, 10)).isEmpty();

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.status()).isEqualTo("EXPIRED");
    }

    @Test
    void confirmTransferByAdminConfirmsFullOrUnderpaidTransferAndRejectsAlreadyPaid() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "flow-sbx-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-sbx-" + suffix, "SKU-SBX-" + suffix, 10);

        var created = orders.checkout(
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
        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);

        var underpaid = paymentFlow.confirmTransferByAdmin(adminId, session.transactionId(), 50_000L);
        assertThat(underpaid.status()).isEqualTo("UNDERPAID");
        assertThat(underpaid.receivedAmount()).isEqualTo(50_000L);
        assertThat(underpaid.refundStatus()).isEqualTo("PENDING");
        assertThat(orders.getDetail(buyerId, created.id()).status()).isEqualTo("AWAITING_PAYMENT");

        var confirmed = paymentFlow.confirmTransferByAdmin(adminId, session.transactionId(), null);
        assertThat(confirmed.status()).isEqualTo("SUCCESS");
        assertThat(confirmed.receivedAmount()).isEqualTo(130_000L);
        assertThat(orders.getDetail(buyerId, created.id()).status()).isEqualTo("PENDING");
        assertThat(orders.getDetail(buyerId, created.id()).paymentStatus()).isEqualTo("PAID");

        assertThatThrownBy(() -> paymentFlow.confirmTransferByAdmin(adminId, session.transactionId(), null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void multiplePartialTransfersAccumulateAndPromoteToSuccessWhenTotalReached() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopA = approvedShop(ownerId, "flow-multi-a-" + suffix);
        var shopB = approvedShop(ownerId, "flow-multi-b-" + suffix);
        var variantA = variantInShop(ownerId, shopA, "ao-multi-a-" + suffix, "SKU-MU-A-" + suffix, 10);
        var variantB = variantInShop(ownerId, shopB, "ao-multi-b-" + suffix, "SKU-MU-B-" + suffix, 10);

        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(
            buyerId,
            new CartCheckoutRequest("An", "0901234567", "Ha Noi", null, PaymentMethod.VIETQR)
        );
        var txnRef = Objects.requireNonNull(placed.getFirst().paymentTxnRef());

        var first = paymentFlow.processIncomingTransfer("CK " + txnRef, 150_000L, "FT-1", "{\"id\":\"FT-1\"}");
        assertThat(first.outcome()).isEqualTo(TransferOutcome.UNDERPAID);
        assertThat(first.receivedAmount()).isEqualTo(150_000L);

        var dupFirst = paymentFlow.processIncomingTransfer("CK " + txnRef, 150_000L, "FT-1", "{\"id\":\"FT-1\"}");
        assertThat(dupFirst.outcome()).isEqualTo(TransferOutcome.ALREADY_PROCESSED);
        assertThat(dupFirst.receivedAmount()).isEqualTo(150_000L);

        var second = paymentFlow.processIncomingTransfer("CK " + txnRef, 100_000L, "FT-2", "{\"id\":\"FT-2\"}");
        assertThat(second.outcome()).isEqualTo(TransferOutcome.UNDERPAID);
        assertThat(second.receivedAmount()).isEqualTo(250_000L);

        var sessionAfterSecond = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(sessionAfterSecond.status()).isEqualTo("UNDERPAID");
        assertThat(sessionAfterSecond.receivedAmount()).isEqualTo(250_000L);
        assertThat(sessionAfterSecond.refundStatus()).isEqualTo("PENDING");
        assertThat(sessionAfterSecond.refundAmount()).isEqualTo(250_000L);

        var third = paymentFlow.processIncomingTransfer("CK " + txnRef, 110_000L, "FT-3", "{\"id\":\"FT-3\"}");
        assertThat(third.outcome()).isEqualTo(TransferOutcome.SUCCESS);
        assertThat(third.receivedAmount()).isEqualTo(360_000L);

        var sessionAfterThird = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(sessionAfterThird.status()).isEqualTo("SUCCESS");
        assertThat(sessionAfterThird.receivedAmount()).isEqualTo(360_000L);
        assertThat(sessionAfterThird.refundStatus()).isEqualTo("NONE");
        assertThat(sessionAfterThird.refundAmount()).isZero();
        assertThat(sessionAfterThird.refundReason()).isNull();

        for (var placedOrder : placed) {
            var detail = orders.getDetail(buyerId, placedOrder.id());
            assertThat(detail.status()).isEqualTo("PENDING");
            assertThat(detail.paymentStatus()).isEqualTo("PAID");
        }
    }

    @Test
    void multiplePartialTransfersOverpayingOnFinalTransferFlagsOnlyOverpaidRemainder() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopA = approvedShop(ownerId, "flow-mop-a-" + suffix);
        var shopB = approvedShop(ownerId, "flow-mop-b-" + suffix);
        var variantA = variantInShop(ownerId, shopA, "ao-mop-a-" + suffix, "SKU-MO-A-" + suffix, 10);
        var variantB = variantInShop(ownerId, shopB, "ao-mop-b-" + suffix, "SKU-MO-B-" + suffix, 10);

        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 2));

        var placed = carts.checkout(
            buyerId,
            new CartCheckoutRequest("An", "0901234567", "Ha Noi", null, PaymentMethod.VIETQR)
        );
        var txnRef = Objects.requireNonNull(placed.getFirst().paymentTxnRef());

        var first = paymentFlow.processIncomingTransfer("CK " + txnRef, 150_000L, "FT-A", "{}");
        assertThat(first.outcome()).isEqualTo(TransferOutcome.UNDERPAID);

        var second = paymentFlow.processIncomingTransfer("CK " + txnRef, 250_000L, "FT-B", "{}");
        assertThat(second.outcome()).isEqualTo(TransferOutcome.SUCCESS);
        assertThat(second.receivedAmount()).isEqualTo(400_000L);

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.status()).isEqualTo("SUCCESS");
        assertThat(session.receivedAmount()).isEqualTo(400_000L);
        assertThat(session.refundStatus()).isEqualTo("PENDING");
        assertThat(session.refundAmount()).isEqualTo(40_000L);
        assertThat(session.refundReason()).isEqualTo("Khách chuyển dư tiền");
    }

    @Test
    void concurrentWebhookAndExpiryJobDoNotDeadlock() throws Exception {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "flow-dl-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-dl-" + suffix, "SKU-DL-" + suffix, 10);

        var created = orders.checkout(
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

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var webhookFuture = pool.submit(() -> {
                ready.countDown();
                start.await();
                return paymentFlow.processIncomingTransfer("CK " + txnRef, created.total(), "FT-CONC", "{}");
            });
            var expiryFuture = pool.submit(() -> {
                ready.countDown();
                start.await();
                return orders.systemExpireAwaitingPaymentOrder(created.id());
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var transferRes = webhookFuture.get(10, TimeUnit.SECONDS);
            var expiredRes = expiryFuture.get(10, TimeUnit.SECONDS);

            assertThat(transferRes.outcome()).isEqualTo(TransferOutcome.SUCCESS);
            var finalOrder = orders.getDetail(buyerId, created.id());
            if (Boolean.TRUE.equals(expiredRes)) {
                assertThat(finalOrder.status()).isEqualTo("CANCELLED");
                assertThat(finalOrder.paymentStatus()).isEqualTo("REFUND_PENDING");
            } else {
                assertThat(finalOrder.status()).isEqualTo("PENDING");
                assertThat(finalOrder.paymentStatus()).isEqualTo("PAID");
            }
        }
    }

    @Test
    void concurrentRetryVietQrForSameOrderReturnsSinglePendingSession() throws Exception {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopA = approvedShop(ownerId, "flow-cretry-a-" + suffix);
        var shopB = approvedShop(ownerId, "flow-cretry-b-" + suffix);
        var variantA = variantInShop(ownerId, shopA, "ao-cra-" + suffix, "SKU-CRA-" + suffix, 10);
        var variantB = variantInShop(ownerId, shopB, "ao-crb-" + suffix, "SKU-CRB-" + suffix, 10);

        carts.addItem(buyerId, new CartItemRequest(variantA, 1));
        carts.addItem(buyerId, new CartItemRequest(variantB, 1));

        var placed = carts.checkout(
            buyerId,
            new CartCheckoutRequest("An", "0901234567", "Ha Noi", null, PaymentMethod.VIETQR)
        );
        var initialTxnRef = Objects.requireNonNull(placed.getFirst().paymentTxnRef());
        paymentFlow.processIncomingTransfer("CK " + initialTxnRef, 50_000L, "FT-CR-UNDER", "{}");

        var firstOrderId = placed.getFirst().id();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var f1 = pool.submit(() -> {
                ready.countDown();
                start.await();
                return paymentFlow.retryForOrder(buyerId, firstOrderId);
            });
            var f2 = pool.submit(() -> {
                ready.countDown();
                start.await();
                return paymentFlow.retryForOrder(buyerId, firstOrderId);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var s1 = f1.get(10, TimeUnit.SECONDS);
            var s2 = f2.get(10, TimeUnit.SECONDS);

            assertThat(s1.txnRef()).isEqualTo(s2.txnRef());
            var pendingCount = jdbc.sql("""
                    SELECT COUNT(*)
                    FROM payment_transaction pt
                    JOIN payment_transaction_order pto ON pto.transaction_id = pt.id
                    WHERE pto.order_id = :orderId
                      AND pt.status = 'PENDING'
                    """)
                .param("orderId", firstOrderId)
                .query(Long.class)
                .single();
            assertThat(pendingCount).isEqualTo(1L);
        }
    }
}
