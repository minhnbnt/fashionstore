package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.cart.CheckoutPreviewRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.voucher.CreateShopVoucherRequest;
import com.minhnbnt.fashionstore.features.cart.CartService;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.payment.PaymentFlowService;
import com.minhnbnt.fashionstore.features.payment.TransferOutcome;
import com.minhnbnt.fashionstore.features.payment.repository.PaymentTransactionRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.features.voucher.repository.ShopVoucherRepository;
import com.minhnbnt.fashionstore.features.voucher.service.VoucherService;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.model.VoucherDiscountType;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "test.isolation=OrderVoucherIntegrationTest")
class OrderVoucherIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReturnRequestService returnRequestService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private ShopVoucherRepository voucherRepository;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private UserService userService;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private PaymentTransactionRepository paymentTransactions;

    @Autowired
    private PaymentFlowService paymentFlow;

    @Autowired
    private SellerSettlementRepository settlements;

    @Autowired
    private Clock clock;

    private UUID createUser() {
        var id = UUID.randomUUID();
        userService.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "User " + id));
        return id;
    }

    private UUID createApprovedShop(UUID ownerId, String slugPrefix) {
        var adminId = createUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var slug = slugPrefix + "-" + suffix;
        var shop = shopService.register(ownerId, ShopTestFixtures.createRequest(uploads, "Shop " + slug, slug));
        shopService.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID createVariant(UUID ownerId, UUID shopId, String slugPrefix, long price, int stock) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var slug = slugPrefix + "-" + suffix;
        var sku = "SKU-" + suffix.toUpperCase(Locale.ROOT);
        var created = catalogService.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Product " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Black", price, null, stock, true))
            )
        );
        var detail = catalogService.getDetail(created.id());
        return detail.variants().getFirst().id();
    }

    @Test
    void cartPreviewAndMultiShopCheckoutAppliesPerShopVouchersAndUpdatesVietQrTotal() {
        var owner1 = createUser();
        var owner2 = createUser();
        var buyerId = createUser();

        var shop1Id = createApprovedShop(owner1, "voucher-s1");
        var shop2Id = createApprovedShop(owner2, "voucher-s2");

        var variant1 = createVariant(owner1, shop1Id, "p1", 150_000L, 10);
        var variant2 = createVariant(owner2, shop2Id, "p2", 200_000L, 10);

        var now = clock.instant();
        var startsAt = now.minusSeconds(300);
        var endsAt = now.plusSeconds(7200);

        var shop1Voucher = voucherService.create(
            owner1,
            shop1Id,
            new CreateShopVoucherRequest(
                "SHOP1V50K",
                "Giảm 50K Shop 1",
                "Đơn từ 200K",
                VoucherDiscountType.FIXED_AMOUNT,
                50_000L,
                null,
                200_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                startsAt,
                endsAt,
                true,
                null
            )
        );

        voucherService.create(
            owner2,
            shop2Id,
            new CreateShopVoucherRequest(
                "SHOP2MIN400",
                "Giảm 40K đơn từ 400K",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                40_000L,
                null,
                400_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                startsAt,
                endsAt,
                true,
                null
            )
        );

        // Cart: Shop 1 has 2 x 150_000 = 300_000 (shipping 30_000); Shop 2 has 1 x 200_000 = 200_000 (shipping 30_000)
        cartService.addItem(buyerId, new CartItemRequest(variant1, 2));
        cartService.addItem(buyerId, new CartItemRequest(variant2, 1));

        // 1. Preview with valid voucher on Shop 1 and no voucher on Shop 2
        var preview = cartService.previewCheckout(
            buyerId,
            new CheckoutPreviewRequest(null, Map.of(shop1Id, "SHOP1V50K"))
        );

        assertThat(preview.subtotal()).isEqualTo(500_000L);
        assertThat(preview.shippingFee()).isEqualTo(60_000L);
        assertThat(preview.discountAmount()).isEqualTo(50_000L);
        assertThat(preview.total()).isEqualTo(510_000L);
        assertThat(preview.shops()).hasSize(2);

        var shop1Preview = preview.shops().get(0);
        assertThat(shop1Preview.shopId()).isEqualTo(shop1Id);
        assertThat(shop1Preview.subtotal()).isEqualTo(300_000L);
        assertThat(shop1Preview.shippingFee()).isEqualTo(30_000L);
        assertThat(shop1Preview.voucherCode()).isEqualTo("SHOP1V50K");
        assertThat(shop1Preview.discountAmount()).isEqualTo(50_000L);
        assertThat(shop1Preview.voucherError()).isNull();
        assertThat(shop1Preview.total()).isEqualTo(280_000L);

        var shop2Preview = preview.shops().get(1);
        assertThat(shop2Preview.shopId()).isEqualTo(shop2Id);
        assertThat(shop2Preview.subtotal()).isEqualTo(200_000L);
        assertThat(shop2Preview.shippingFee()).isEqualTo(30_000L);
        assertThat(shop2Preview.voucherCode()).isNull();
        assertThat(shop2Preview.discountAmount()).isZero();
        assertThat(shop2Preview.voucherError()).isNull();
        assertThat(shop2Preview.total()).isEqualTo(230_000L);

        // 2. Preview with ineligible voucher on Shop 2 does NOT throw, returns discountAmount = 0 and voucherError
        var previewWithIneligible = cartService.previewCheckout(
            buyerId,
            new CheckoutPreviewRequest(
                null,
                Map.of(shop1Id, "SHOP1V50K", shop2Id, "SHOP2MIN400")
            )
        );
        var shop2Ineligible = previewWithIneligible.shops().get(1);
        assertThat(shop2Ineligible.voucherCode()).isEqualTo("SHOP2MIN400");
        assertThat(shop2Ineligible.discountAmount()).isZero();
        assertThat(shop2Ineligible.voucherError()).isNotNull();
        assertThat(shop2Ineligible.total()).isEqualTo(230_000L);
        assertThat(previewWithIneligible.discountAmount()).isEqualTo(50_000L);
        assertThat(previewWithIneligible.total()).isEqualTo(510_000L);

        // 3. Checkout with VIETQR and Shop 1 voucher creates 2 orders and 1 shared payment_transaction (510_000L)
        var placedOrders = cartService.checkout(
            buyerId,
            new CartCheckoutRequest(
                "An",
                "0901234567",
                "Ha Noi",
                null,
                PaymentMethod.VIETQR,
                Map.of(shop1Id, "SHOP1V50K")
            )
        );

        assertThat(placedOrders).hasSize(2);
        var placedShop1 = placedOrders.get(0);
        assertThat(placedShop1.shopId()).isEqualTo(shop1Id);
        assertThat(placedShop1.voucherId()).isEqualTo(shop1Voucher.id());
        assertThat(placedShop1.voucherCode()).isEqualTo("SHOP1V50K");
        assertThat(placedShop1.discountAmount()).isEqualTo(50_000L);
        assertThat(placedShop1.subtotal()).isEqualTo(300_000L);
        assertThat(placedShop1.shippingFee()).isEqualTo(30_000L);
        assertThat(placedShop1.total()).isEqualTo(280_000L);

        var placedShop2 = placedOrders.get(1);
        assertThat(placedShop2.shopId()).isEqualTo(shop2Id);
        assertThat(placedShop2.voucherId()).isNull();
        assertThat(placedShop2.voucherCode()).isNull();
        assertThat(placedShop2.discountAmount()).isZero();
        assertThat(placedShop2.total()).isEqualTo(230_000L);

        var txnRef = Objects.requireNonNull(placedShop1.paymentTxnRef());
        assertThat(placedShop2.paymentTxnRef()).isEqualTo(txnRef);

        var tx = paymentTransactions.findByTxnRef(txnRef).orElseThrow();
        assertThat(tx.getAmount()).isEqualTo(510_000L);
    }

    @Test
    void cancellingOrExpiringOrderReleasesVoucherQuotaForReuse() {
        var ownerId = createUser();
        var buyerId = createUser();
        var shopId = createApprovedShop(ownerId, "quota-shop");
        var variantId = createVariant(ownerId, shopId, "p-quota", 200_000L, 20);

        var now = clock.instant();
        var voucher = voucherService.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "ONCE30K",
                "Dùng 1 lần duy nhất",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                30_000L,
                null,
                100_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                1,
                1,
                now.minusSeconds(300),
                now.plusSeconds(7200),
                true,
                null
            )
        );

        var checkoutReq = new CheckoutRequest(
            shopId,
            List.of(new CheckoutItemRequest(variantId, 1)),
            "An",
            "0901234567",
            "Ha Noi",
            PaymentMethod.COD,
            "ONCE30K"
        );

        // 1. First order succeeds and consumes the sole voucher quota
        var order1 = orderService.checkout(buyerId, checkoutReq);
        assertThat(order1.voucherId()).isEqualTo(voucher.id());
        assertThat(order1.voucherCode()).isEqualTo("ONCE30K");
        assertThat(order1.discountAmount()).isEqualTo(30_000L);
        assertThat(order1.total()).isEqualTo(200_000L);

        var afterFirst = voucherRepository.findById(voucher.id()).orElseThrow();
        assertThat(afterFirst.getUsedCount()).isEqualTo(1);

        // Second checkout while first order is active throws 409 CONFLICT
        assertThatThrownBy(() -> orderService.checkout(buyerId, checkoutReq))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // 2. Buyer cancels order1 -> usedCount drops to 0 -> buyer can reuse voucher
        orderService.cancel(buyerId, order1.id());
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isZero();

        // 3. Place VietQR order with same voucher -> usedCount == 1 -> expire payment -> usedCount == 0
        var vietQrReq = new CheckoutRequest(
            shopId,
            List.of(new CheckoutItemRequest(variantId, 1)),
            "An",
            "0901234567",
            "Ha Noi",
            PaymentMethod.VIETQR,
            "ONCE30K"
        );
        var order2 = orderService.checkout(buyerId, vietQrReq);
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isEqualTo(1);

        var expired = orderService.systemExpireAwaitingPaymentOrder(order2.id());
        assertThat(expired).isTrue();
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isZero();

        // 4. Place COD order again -> seller cancels -> usedCount == 0 -> buyer can place order4
        var order3 = orderService.checkout(buyerId, checkoutReq);
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isEqualTo(1);

        orderService.sellerCancel(ownerId, order3.id(), new CancelOrderRequest("OUT_OF_STOCK", "Hết màu"));
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isZero();

        var order4 = orderService.checkout(buyerId, checkoutReq);
        assertThat(order4.voucherId()).isEqualTo(voucher.id());
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void settlementGrossAmountAndReturnRefundUseNetMerchandiseTotalAfterVoucher() {
        var ownerId = createUser();
        var buyerId = createUser();
        var shopId = createApprovedShop(ownerId, "settle-shop");
        var variantId = createVariant(ownerId, shopId, "p-settle", 100_000L, 10);

        var now = clock.instant();
        voucherService.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "SAVE40K",
                "Giảm 40K",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                40_000L,
                null,
                150_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                2,
                now.minusSeconds(300),
                now.plusSeconds(7200),
                true,
                null
            )
        );

        // Place VietQR order: subtotal = 200_000, discountAmount = 40_000, shippingFee = 30_000, total = 190_000
        var created = orderService.checkout(
            buyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(variantId, 2)),
                "An",
                "0901234567",
                "Ha Noi",
                PaymentMethod.VIETQR,
                "SAVE40K"
            )
        );
        assertThat(created.subtotal()).isEqualTo(200_000L);
        assertThat(created.discountAmount()).isEqualTo(40_000L);
        assertThat(created.shippingFee()).isEqualTo(30_000L);
        assertThat(created.total()).isEqualTo(190_000L);

        var txnRef = Objects.requireNonNull(created.paymentTxnRef());
        var transfer = paymentFlow.processIncomingTransfer(
            "CK " + txnRef,
            created.total(),
            "FT-" + txnRef,
            "{}"
        );
        assertThat(transfer.outcome()).isEqualTo(TransferOutcome.SUCCESS);

        // Settlement must use netMerchandiseTotal = 200_000 - 40_000 = 160_000 (5% fee = 8_000, net = 152_000)
        var settlement = settlements.findByOrderId(created.id()).orElseThrow();
        assertThat(settlement.getGrossAmount()).isEqualTo(160_000L);
        assertThat(settlement.getPlatformFee()).isEqualTo(8_000L);
        assertThat(settlement.getNetAmount()).isEqualTo(152_000L);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.HOLDING);

        // Deliver order so buyer can request return
        orderService.confirm(ownerId, created.id());
        orderService.ship(ownerId, created.id());
        orderService.deliver(ownerId, created.id());

        var returnReq = returnRequestService.create(
            buyerId,
            created.id(),
            new CreateReturnRequest("DAMAGED", "Lỗi đường may", List.of())
        );

        // Seller tries to approve refundAmount = 180_000 (> 160_000 net merchandise total) -> 400 BAD_REQUEST
        assertThatThrownBy(() -> returnRequestService.approve(
            ownerId,
            returnReq.id(),
            new ApproveReturnRequest("REFUND", 180_000L, "Hoàn quá số tiền hàng thực trả")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // Seller approves with refundAmount = 160_000L and completes
        returnRequestService.approve(
            ownerId,
            returnReq.id(),
            new ApproveReturnRequest("REFUND", 160_000L, "Đồng ý hoàn tiền hàng sau giảm")
        );
        var completed = returnRequestService.complete(ownerId, returnReq.id(), "Đã nhận lại hàng");
        assertThat(completed.status()).isEqualTo("COMPLETED");

        var afterReturnSettlement = settlements.findByOrderId(created.id()).orElseThrow();
        assertThat(afterReturnSettlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);

        var session = paymentFlow.getSessionForViewer(buyerId, false, txnRef);
        assertThat(session.refundStatus()).isEqualTo("PENDING");
        assertThat(session.refundAmount()).isEqualTo(160_000L);
    }
}
