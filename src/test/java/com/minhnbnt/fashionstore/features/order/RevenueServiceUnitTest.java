package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerWithdrawalRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private ShopRepository shops;

    @Mock
    private ShopAccessChecker access;

    @InjectMocks
    private RevenueService revenueService;

    @Test
    void getSummaryCalculatesCorrectBalances() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(orders.sumDeliveredRevenue(shopId))
            .thenReturn(new OrderRepository.RevenueSummaryRow(100000L, 1L, 100000L));
        when(settlements.sumAvailableBalance(shopId)).thenReturn(95000L);
        when(settlements.sumHoldingBalance(shopId)).thenReturn(5000L);

        var summary = revenueService.getSummary(userId, shopId);

        verify(access).requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);
        assertThat(summary.totalRevenue()).isEqualTo(100000L);
        assertThat(summary.walletBalance()).isEqualTo(95000L);
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
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));

        var req = new CreateWithdrawalRequest(shopId, 100000L, "Vietcombank", "999888777", "Nguyen Van A");
        var withdrawal = SellerWithdrawal.request(shopId, 100000L, "Vietcombank", "999888777", "Nguyen Van A");
        when(withdrawals.save(any(SellerWithdrawal.class))).thenReturn(withdrawal);

        var resp = revenueService.createWithdrawal(userId, req);

        assertThat(resp.amount()).isEqualTo(100000L);
        assertThat(resp.status()).isEqualTo("REQUESTED");
        verify(withdrawals).save(any(SellerWithdrawal.class));
    }

    @Test
    void createWithdrawalThrowsBadRequestWhenAmountExceedsBalance() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(settlements.sumAvailableBalance(shopId)).thenReturn(0L);

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

        verify(settlements).save(any());
    }
}
