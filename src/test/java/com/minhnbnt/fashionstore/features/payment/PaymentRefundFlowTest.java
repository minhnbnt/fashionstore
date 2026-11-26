package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.BuyerCancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationRepository;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.AdminCancelReason;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import com.minhnbnt.fashionstore.model.RefundStatus;
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
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "test.isolation=PaymentRefundFlowTest")
class PaymentRefundFlowTest {

    @Autowired
    private PaymentFlowService paymentFlow;

    @Autowired
    private OrderService orders;

    @Autowired
    private ReturnRequestService returnRequests;

    @Autowired
    private RevenueService revenue;

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
    private ConfigAuditService configAudit;

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

    private OrderResponse createPaidVietQrOrder(UUID buyerId, UUID shopId, UUID variantId, int quantity) {
        var created = orders.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, quantity)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.VIETQR
            )
        );
        var txnRef = Objects.requireNonNull(created.paymentTxnRef());
        var result = paymentFlow.processIncomingTransfer(
            "CK " + txnRef,
            created.total(),
            "FT-" + txnRef,
            "{}"
        );
        assertThat(result.outcome()).isEqualTo(TransferOutcome.SUCCESS);
        return orders.getDetail(buyerId, created.id())
            .withPaymentSession(created.paymentTxnRef(), created.paymentQrUrl(), created.paymentExpiresAt());
    }

    @Test
    void cancellingPaidVietQrOrderCancelsHoldingSettlementAndMovesToRefundPending() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "refund-cancel-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-rc-" + suffix, "SKU-RC-" + suffix, 20);

        // 1. Buyer cancels PAID order in PENDING
        var buyerOrder = createPaidVietQrOrder(buyerId, shopId, variantId, 2);
        var buyerTxnRef = Objects.requireNonNull(buyerOrder.paymentTxnRef());
        assertThat(settlements.findByOrderId(buyerOrder.id()).orElseThrow().getStatus())
            .isEqualTo(SettlementStatus.HOLDING);

        var afterBuyerCancel = orders.cancel(
            buyerId,
            buyerOrder.id(),
            new BuyerCancelOrderRequest("CHANGE_MIND", "Khong mua nua")
        );
        assertThat(afterBuyerCancel.status()).isEqualTo("CANCELLED");
        assertThat(afterBuyerCancel.paymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(settlements.findByOrderId(buyerOrder.id()).orElseThrow().getStatus())
            .isEqualTo(SettlementStatus.CANCELLED);
        var buyerSession = paymentFlow.getSessionForViewer(buyerId, false, buyerTxnRef);
        assertThat(buyerSession.refundStatus()).isEqualTo("PENDING");
        assertThat(buyerSession.refundAmount()).isEqualTo(buyerOrder.total());
        assertThat(buyerSession.refundReason())
            .contains("Đơn hàng #" + buyerOrder.code() + " bị hủy sau khi đã thanh toán");

        // 2. Seller cancels PAID order in CONFIRMED
        var sellerOrder = createPaidVietQrOrder(buyerId, shopId, variantId, 1);
        var sellerTxnRef = Objects.requireNonNull(sellerOrder.paymentTxnRef());
        orders.confirm(ownerId, sellerOrder.id());

        var afterSellerCancel = orders.sellerCancel(
            ownerId,
            sellerOrder.id(),
            new CancelOrderRequest("Hết hàng", "Kho hết mẫu này")
        );
        assertThat(afterSellerCancel.status()).isEqualTo("CANCELLED");
        assertThat(afterSellerCancel.paymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(settlements.findByOrderId(sellerOrder.id()).orElseThrow().getStatus())
            .isEqualTo(SettlementStatus.CANCELLED);
        var sellerSession = paymentFlow.getSessionForViewer(buyerId, false, sellerTxnRef);
        assertThat(sellerSession.refundStatus()).isEqualTo("PENDING");
        assertThat(sellerSession.refundAmount()).isEqualTo(sellerOrder.total());

        // 3. Admin cancels PAID order in CONFIRMED
        var adminOrder = createPaidVietQrOrder(buyerId, shopId, variantId, 1);
        var adminTxnRef = Objects.requireNonNull(adminOrder.paymentTxnRef());
        orders.confirm(ownerId, adminOrder.id());

        var afterAdminCancel = orders.cancelByAdmin(
            adminId,
            adminOrder.id(),
            AdminCancelReason.BUYER_REQUEST,
            "Khach yeu cau admin huy"
        );
        assertThat(afterAdminCancel.status()).isEqualTo("CANCELLED");
        assertThat(afterAdminCancel.paymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(settlements.findByOrderId(adminOrder.id()).orElseThrow().getStatus())
            .isEqualTo(SettlementStatus.CANCELLED);
        var adminSession = paymentFlow.getSessionForViewer(buyerId, false, adminTxnRef);
        assertThat(adminSession.refundStatus()).isEqualTo("PENDING");
        assertThat(adminSession.refundAmount()).isEqualTo(adminOrder.total());

        // 4. System auto-cancels expired PAID order in PENDING
        var sysOrder = createPaidVietQrOrder(buyerId, shopId, variantId, 1);
        var sysTxnRef = Objects.requireNonNull(sysOrder.paymentTxnRef());

        orders.systemCancelExpiredOrder(sysOrder.id());

        var afterSysCancel = orders.getDetail(buyerId, sysOrder.id());
        assertThat(afterSysCancel.status()).isEqualTo("CANCELLED");
        assertThat(afterSysCancel.paymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(settlements.findByOrderId(sysOrder.id()).orElseThrow().getStatus())
            .isEqualTo(SettlementStatus.CANCELLED);
        var sysSession = paymentFlow.getSessionForViewer(buyerId, false, sysTxnRef);
        assertThat(sysSession.refundStatus()).isEqualTo("PENDING");
        assertThat(sysSession.refundAmount()).isEqualTo(sysOrder.total());

        // All 4 cancelled orders restocked variant back to 20 and holding balance is 0
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(20);
        assertThat(revenue.getSummary(ownerId, shopId).holdingBalance()).isZero();
    }

    @Test
    void completingFullReturnRestocksInventoryCancelsSettlementAndFlagsRefundPending() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "refund-fullret-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-fr-" + suffix, "SKU-FR-" + suffix, 10);

        var order = createPaidVietQrOrder(buyerId, shopId, variantId, 2);
        var txnRef = Objects.requireNonNull(order.paymentTxnRef());
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(8);

        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id());
        orders.deliver(ownerId, order.id());

        var createdRet = returnRequests.create(
            buyerId,
            order.id(),
            new CreateReturnRequest("DAMAGED", "Sản phẩm bị lỗi đường may", List.of())
        );
        returnRequests.approve(
            ownerId,
            createdRet.id(),
            new ApproveReturnRequest("REFUND", order.subtotal(), "Kho HN nhận hàng hoàn")
        );
        var completedRet = returnRequests.complete(ownerId, createdRet.id(), "Đã nhận đủ hàng hoàn");

        assertThat(completedRet.status()).isEqualTo("COMPLETED");
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);

        var returnMovements = jdbc.sql("""
                SELECT COUNT(*)
                FROM stock_movement
                WHERE variant_id = :variantId
                  AND reason = 'ORDER_RETURN'
                  AND reference_id = :orderId
                  AND delta = 2
                """)
            .param("variantId", variantId)
            .param("orderId", order.id())
            .query(Long.class)
            .single();
        assertThat(returnMovements).isEqualTo(1L);

        var settlement = settlements.findByOrderId(order.id()).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
        assertThat(revenue.getSummary(ownerId, shopId).holdingBalance()).isZero();
        assertThat(revenue.getSummary(ownerId, shopId).walletBalance()).isZero();

        var reloadedOrder = orders.getDetail(buyerId, order.id());
        assertThat(reloadedOrder.paymentStatus()).isEqualTo("REFUND_PENDING");

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.refundStatus()).isEqualTo("PENDING");
        assertThat(session.refundAmount()).isEqualTo(order.subtotal());
        assertThat(session.refundReason()).contains("Hoàn tiền đổi trả đơn #" + order.code());
    }

    @Test
    void completingPartialReturnAdjustsSettlementWith5PercentFeeOnRemainderAndReleasesToShop() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "refund-partret-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-pr-" + suffix, "SKU-PR-" + suffix, 10);

        // subtotal = 200_000, shippingFee = 30_000, total = 230_000
        var order = createPaidVietQrOrder(buyerId, shopId, variantId, 2);
        var txnRef = Objects.requireNonNull(order.paymentTxnRef());

        orders.confirm(ownerId, order.id());
        orders.ship(ownerId, order.id());
        orders.deliver(ownerId, order.id());

        var createdRet = returnRequests.create(
            buyerId,
            order.id(),
            new CreateReturnRequest("NOT_AS_DESCRIBED", "Thiếu phụ kiện đi kèm", List.of())
        );
        returnRequests.approve(
            ownerId,
            createdRet.id(),
            new ApproveReturnRequest("PARTIAL_REFUND", 50_000L, "Hoàn 50.000đ giữ hàng")
        );
        var completedRet = returnRequests.complete(ownerId, createdRet.id(), "Chấp nhận hoàn 50k");

        assertThat(completedRet.status()).isEqualTo("COMPLETED");
        // Customer keeps physical goods on PARTIAL_REFUND -> stock remains 8
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(8);

        var settlement = settlements.findByOrderId(order.id()).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.RELEASED);
        assertThat(settlement.getGrossAmount()).isEqualTo(150_000L);
        assertThat(settlement.getPlatformFee()).isEqualTo(7_500L);
        assertThat(settlement.getNetAmount()).isEqualTo(142_500L);
        assertThat(settlement.getReleasedAt()).isNotNull();

        var summary = revenue.getSummary(ownerId, shopId);
        assertThat(summary.holdingBalance()).isZero();
        assertThat(summary.walletBalance()).isEqualTo(142_500L);

        var reloadedOrder = orders.getDetail(buyerId, order.id());
        assertThat(reloadedOrder.paymentStatus()).isEqualTo("REFUND_PENDING");

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.refundStatus()).isEqualTo("PENDING");
        assertThat(session.refundAmount()).isEqualTo(50_000L);
    }

    @Test
    void adminMarkTransactionRefundedTransitionsTransactionAndOrderToRefunded() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(ownerId, "refund-admin-" + suffix);
        var variantId = variantInShop(ownerId, shopId, "ao-ar-" + suffix, "SKU-AR-" + suffix, 10);

        var order = createPaidVietQrOrder(buyerId, shopId, variantId, 1);
        var txnRef = Objects.requireNonNull(order.paymentTxnRef());

        orders.cancel(buyerId, order.id(), new BuyerCancelOrderRequest("CHANGE_MIND", "Hủy đơn"));

        var page = paymentFlow.searchTransactionsForAdmin(
            PaymentTransactionStatus.SUCCESS,
            RefundStatus.PENDING,
            order.code(),
            0,
            20
        );
        assertThat(page.total()).isEqualTo(1L);
        var pendingTx = page.items().getFirst();
        assertThat(pendingTx.txnRef()).isEqualTo(txnRef);
        assertThat(pendingTx.buyerId()).isEqualTo(buyerId);
        assertThat(pendingTx.buyerEmail()).isEqualTo("u-" + buyerId + "@t.local");
        assertThat(pendingTx.refundStatus()).isEqualTo("PENDING");
        assertThat(pendingTx.refundAmount()).isEqualTo(order.total());

        var note = "Đã chuyển khoản hoàn tiền qua MB Bank";
        var refundedTx = paymentFlow.markTransactionRefunded(adminId, pendingTx.id(), note);

        assertThat(refundedTx.refundStatus()).isEqualTo("REFUNDED");
        assertThat(refundedTx.refundNote()).isEqualTo(note);
        assertThat(refundedTx.refundedAt()).isNotNull();
        assertThat(refundedTx.orders()).hasSize(1);
        assertThat(refundedTx.orders().getFirst().paymentStatus()).isEqualTo("REFUNDED");

        var reloadedOrder = orders.getDetail(buyerId, order.id());
        assertThat(reloadedOrder.paymentStatus()).isEqualTo("REFUNDED");

        var buyerNotifs = notifications.findPage(buyerId, null, 0, 10);
        assertThat(buyerNotifs)
            .anySatisfy(n -> {
                assertThat(n.type()).isEqualTo("ORDER_REFUNDED");
                assertThat(n.body()).contains(note);
            });

        var audits = configAudit.page(ConfigAuditEntity.PAYMENT_TRANSACTION, 0, 10);
        assertThat(audits.items())
            .anySatisfy(a -> {
                assertThat(a.entityKey()).isEqualTo(txnRef);
                assertThat(a.action()).isEqualTo("REFUND");
            });

        assertThatThrownBy(() -> paymentFlow.markTransactionRefunded(adminId, pendingTx.id(), "Hoàn tiền lần 2"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }
}
