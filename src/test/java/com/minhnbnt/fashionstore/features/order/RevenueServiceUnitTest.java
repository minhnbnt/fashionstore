package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerWithdrawalRepository;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueServiceUnitTest {

    @Mock
    private SellerSettlementRepository settlements;

    @Mock
    private SellerWithdrawalRepository withdrawals;

    @Mock
    private OrderRepository orders;

    @Mock
    private OrderItemRepository items;

    @Mock
    private ShopService shops;

    @Mock
    private ShopAccessChecker access;

    @Mock
    private ShopAuditService audit;

    @Mock
    private ConfigAuditService configAudit;

    @InjectMocks
    private RevenueService revenueService;

    @Test
    void getSummaryCalculatesCorrectBalances() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(orders.sumDeliveredRevenue(shopId))
            .thenReturn(new OrderRepository.RevenueSummaryRow(100000L, 1L, 100000L));
        when(settlements.sumAvailableBalance(shopId)).thenReturn(95000L);
        when(withdrawals.sumActiveWithdrawals(shopId)).thenReturn(15000L);
        when(settlements.sumHoldingBalance(shopId)).thenReturn(5000L);

        var summary = revenueService.getSummary(userId, shopId);

        verify(access).requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);
        assertThat(summary.totalRevenue()).isEqualTo(100000L);
        assertThat(summary.walletBalance()).isEqualTo(80000L);
        assertThat(summary.holdingBalance()).isEqualTo(5000L);
    }

    @Test
    void createWithdrawalDeductsBalanceCorrectly() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        var shop = ShopTestFixtures.shop("My Shop", "my-shop", "Mo ta shop");
        shop.updateProfile(
            "0901234567",
            "123 Street",
            "7 days return",
            2,
            "Vietcombank",
            "999888777",
            "Nguyen Van A",
            "Auto reply msg",
            "8h-18h"
        );

        when(settlements.sumAvailableBalance(shopId)).thenReturn(190000L);
        when(withdrawals.sumActiveWithdrawals(shopId)).thenReturn(50000L);
        when(shops.findEntityById(shopId)).thenReturn(Optional.of(shop));

        var req = new CreateWithdrawalRequest(shopId, 100000L, "Vietcombank", "999888777", "Nguyen Van A");
        var withdrawal = SellerWithdrawal.request(shopId, 100000L, "Vietcombank", "999888777", "Nguyen Van A");
        when(withdrawals.save(any(SellerWithdrawal.class))).thenReturn(withdrawal);

        var resp = revenueService.createWithdrawal(userId, req);

        assertThat(resp.amount()).isEqualTo(100000L);
        assertThat(resp.status()).isEqualTo("REQUESTED");
        verify(withdrawals).save(any(SellerWithdrawal.class));
    }

    @Test
    void createWithdrawalThrowsBadRequestWhenAmountExceedsBalanceAfterActiveWithdrawals() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(settlements.sumAvailableBalance(shopId)).thenReturn(150000L);
        when(withdrawals.sumActiveWithdrawals(shopId)).thenReturn(100000L);

        var req = new CreateWithdrawalRequest(shopId, 100000L, "Vietcombank", "999888777", "Nguyen Van A");

        assertThatThrownBy(() -> revenueService.createWithdrawal(userId, req))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createSettlementForOrderCreatesPendingSettlementWithPlatformFee() {
        var shopId = UUID.randomUUID();
        var orderId = UUID.randomUUID();

        when(settlements.findByOrderId(orderId)).thenReturn(Optional.empty());

        revenueService.createSettlementForOrder(shopId, orderId, 500000L);

        var captor = ArgumentCaptor.forClass(SellerSettlement.class);
        verify(settlements).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getGrossAmount()).isEqualTo(500000L);
        assertThat(saved.getPlatformFee()).isEqualTo(25000L);
        assertThat(saved.getNetAmount()).isEqualTo(475000L);
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.HOLDING);
    }

    @Test
    void releaseCancelAndAdjustSettlementLifecycleMethodsUpdateHoldingSettlement() {
        var shopId = UUID.randomUUID();
        var order1 = UUID.randomUUID();
        var s1 = SellerSettlement.create(shopId, order1, 200000L, 10000L);
        when(settlements.findByOrderId(order1)).thenReturn(Optional.of(s1));

        revenueService.releaseSettlementForOrder(order1);
        assertThat(s1.getStatus()).isEqualTo(SettlementStatus.RELEASED);
        assertThat(s1.getReleasedAt()).isNotNull();
        verify(settlements).save(s1);

        var order2 = UUID.randomUUID();
        var s2 = SellerSettlement.create(shopId, order2, 200000L, 10000L);
        when(settlements.findByOrderId(order2)).thenReturn(Optional.of(s2));

        revenueService.cancelSettlementForOrder(order2);
        assertThat(s2.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
        verify(settlements).save(s2);

        var order3 = UUID.randomUUID();
        var s3 = SellerSettlement.create(shopId, order3, 200000L, 10000L);
        when(settlements.findByOrderId(order3)).thenReturn(Optional.of(s3));

        revenueService.adjustSettlementOnReturn(order3, 50000L);
        assertThat(s3.getGrossAmount()).isEqualTo(150000L);
        assertThat(s3.getPlatformFee()).isEqualTo(7500L);
        assertThat(s3.getNetAmount()).isEqualTo(142500L);
        assertThat(s3.getStatus()).isEqualTo(SettlementStatus.RELEASED);
        verify(settlements).save(s3);
    }

    @Test
    void approveAndRejectWithdrawalTransitionStatusAndRecordConfigAudit() {
        var adminId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var w1 = SellerWithdrawal.request(shopId, 100000L, "Vietcombank", "999888777", "Nguyen Van A");
        when(withdrawals.findById(w1.getId())).thenReturn(Optional.of(w1));
        when(withdrawals.save(any(SellerWithdrawal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shops.getShopName(shopId)).thenReturn("My Shop");

        var approved = revenueService.approveWithdrawal(adminId, w1.getId());
        assertThat(approved.status()).isEqualTo("COMPLETED");
        assertThat(approved.shopName()).isEqualTo("My Shop");
        assertThat(approved.processedAt()).isNotNull();
        verify(configAudit).record(
            eq(ConfigAuditEntity.PAYMENT_TRANSACTION),
            eq(w1.getId().toString()),
            eq("WITHDRAWAL_APPROVE"),
            any(),
            any(),
            eq(adminId)
        );

        assertThatThrownBy(() -> revenueService.rejectWithdrawal(adminId, w1.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
