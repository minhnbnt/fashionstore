package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.payment.VietQrOrderBrief;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.payment.repository.PaymentTransactionRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import com.minhnbnt.fashionstore.model.RefundStatus;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentTransactionServiceTest {

    @Autowired
    private PaymentTransactionService transactions;

    @Autowired
    private PaymentTransactionRepository transactionRepo;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private UploadRepository uploads;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "Buyer " + id));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String name, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest(uploads, name, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    @Test
    void createSessionAggregatesMultipleOrdersAndExposesSessionToOwnerAndAdminOnly() {
        var buyerId = mirroredUser();
        var otherBuyerId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shop1Id = approvedShop(mirroredUser(), "Shop Alpha " + suffix, "shop-alpha-" + suffix);
        var shop2Id = approvedShop(mirroredUser(), "Shop Beta " + suffix, "shop-beta-" + suffix);

        var order1 = orders.save(Order.create(
            "ORD-A-" + suffix,
            shop1Id,
            buyerId,
            150_000L,
            30_000L,
            "An",
            "0901234567",
            "Ha Noi"
        ));
        var order2 = orders.save(Order.create(
            "ORD-B-" + suffix,
            shop2Id,
            buyerId,
            200_000L,
            30_000L,
            "An",
            "0901234567",
            "Ha Noi"
        ));

        var expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        var tx = transactions.createSession(buyerId, List.of(order1, order2), expiresAt);

        assertThat(tx.getAmount()).isEqualTo(180_000L + 230_000L);
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(tx.getRefundStatus()).isEqualTo(RefundStatus.NONE);
        assertThat(tx.getTxnRef()).matches("^DHMQ[A-Z0-9]{10}$");
        assertThat(tx.getQrImageUrl()).contains("amount=410000").contains("addInfo=" + tx.getTxnRef());

        var linkedOrderIds = transactions.findOrderIdsByTransactionId(tx.getId());
        assertThat(linkedOrderIds).containsExactlyInAnyOrder(order1.getId(), order2.getId());

        var session = transactions.getSessionForViewer(tx.getTxnRef(), buyerId, false);
        assertThat(session.transactionId()).isEqualTo(tx.getId());
        assertThat(session.amount()).isEqualTo(410_000L);
        assertThat(session.bankCode()).isEqualTo("MB");
        assertThat(session.orders())
            .extracting(VietQrOrderBrief::orderId)
            .containsExactlyInAnyOrder(order1.getId(), order2.getId());
        assertThat(session.orders())
            .extracting(VietQrOrderBrief::shopName)
            .containsExactlyInAnyOrder("Shop Alpha " + suffix, "Shop Beta " + suffix);

        var adminView = transactions.getSessionForViewer(tx.getTxnRef(), otherBuyerId, true);
        assertThat(adminView.txnRef()).isEqualTo(tx.getTxnRef());

        assertThatThrownBy(() -> transactions.getSessionForViewer(tx.getTxnRef(), otherBuyerId, false))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pendingSessionLookupExpirationAndFlagOrderRefundWorkAcrossLifecycle() {
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shopId = approvedShop(mirroredUser(), "Shop Gamma " + suffix, "shop-gamma-" + suffix);

        var order = orders.save(Order.create(
            "ORD-G-" + suffix,
            shopId,
            buyerId,
            300_000L,
            20_000L,
            "Binh",
            "0909888777",
            "Da Nang"
        ));

        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var tx = transactions.createSession(buyerId, List.of(order), now.plus(15, ChronoUnit.MINUTES));

        var foundPending = transactions.findPendingSessionForOrder(order.getId(), now);
        assertThat(foundPending).isPresent();
        assertThat(foundPending.get().getId()).isEqualTo(tx.getId());

        transactions.expirePendingSessionsForOrder(order.getId(), now.plus(16, ChronoUnit.MINUTES));
        assertThat(transactions.findPendingSessionForOrder(order.getId(), now)).isEmpty();
        var expiredReloaded = transactionRepo.findByTxnRef(tx.getTxnRef()).orElseThrow();
        assertThat(expiredReloaded.getStatus()).isEqualTo(PaymentTransactionStatus.EXPIRED);

        var tx2 = transactions.createSession(buyerId, List.of(order), now.plus(30, ChronoUnit.MINUTES));
        var locked = transactionRepo.findLockedByTxnRef(tx2.getTxnRef()).orElseThrow();
        locked.markSuccess(320_000L, "FT999", "CK " + tx2.getTxnRef(), "{\"id\":\"FT999\"}", now);
        transactionRepo.save(locked);

        transactions.flagOrderRefund(order.getId(), 320_000L, "Đơn hàng bị hủy sau khi thanh toán");

        var afterFlag = transactionRepo.findLockedById(tx2.getId()).orElseThrow();
        assertThat(afterFlag.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(afterFlag.getRefundAmount()).isEqualTo(320_000L);
        assertThat(afterFlag.getRefundReason()).contains("Đơn hàng bị hủy sau khi thanh toán");

        afterFlag.markRefunded(adminId, "Đã chuyển khoản hoàn tiền", now.plusSeconds(60));
        transactionRepo.save(afterFlag);

        var refunded = transactionRepo.findByTxnRef(tx2.getTxnRef()).orElseThrow();
        assertThat(refunded.getRefundStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(refunded.getRefundedBy()).isEqualTo(adminId);
        assertThat(refunded.getRefundNote()).isEqualTo("Đã chuyển khoản hoàn tiền");
    }
}
