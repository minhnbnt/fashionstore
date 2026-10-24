package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
import com.minhnbnt.fashionstore.dto.admin.OrderRevenueTotals;
import com.minhnbnt.fashionstore.features.catalog.ProductReportService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminStatsServiceTest {

    // 2026-10-16 01:00 UTC = 2026-10-16 08:00 giờ Việt Nam
    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private ShopService shops;

    private UserService users;

    private OrderService orders;

    private ProductReportService productReports;

    private AdminStatsService stats;

    @BeforeEach
    void setUp() {
        shops = mock(ShopService.class);
        users = mock(UserService.class);
        orders = mock(OrderService.class);
        productReports = mock(ProductReportService.class);
        stats = new AdminStatsService(shops, users, orders, productReports, Clock.fixed(NOW, ZoneOffset.UTC));

        when(shops.countByStatus()).thenReturn(Map.of(
            ShopStatus.PENDING, 2L,
            ShopStatus.APPROVED, 5L,
            ShopStatus.SUSPENDED, 1L,
            ShopStatus.REJECTED, 0L
        ));
        when(users.countByStatus()).thenReturn(Map.of(AccountStatus.ACTIVE, 9L, AccountStatus.LOCKED, 1L));
        when(users.countByRole()).thenReturn(Map.of(UserRole.CUSTOMER, 6L, UserRole.SHOP, 3L, UserRole.ADMIN, 1L));
        when(orders.countByStatus()).thenReturn(Map.of(
            OrderStatus.PENDING, 4L,
            OrderStatus.CONFIRMED, 3L,
            OrderStatus.SHIPPING, 2L,
            OrderStatus.DELIVERED, 7L,
            OrderStatus.CANCELLED, 1L
        ));
        when(orders.revenueTotals()).thenReturn(new OrderRevenueTotals(1_600_000L, 700_000L));
        when(users.countCreatedPerDay(any(), any())).thenReturn(Map.of());
        when(shops.countCreatedPerDay(any(), any())).thenReturn(Map.of());
        when(orders.totalsPerDay(any(), any())).thenReturn(Map.of());
    }

    @Test
    void sumsUsersShopsAndOrders() {
        var result = stats.get();

        assertThat(result.totalUsers()).isEqualTo(10);
        assertThat(result.lockedUsers()).isEqualTo(1);
        assertThat(result.customerUsers()).isEqualTo(6);
        assertThat(result.sellerUsers()).isEqualTo(3);
        assertThat(result.adminUsers()).isEqualTo(1);

        assertThat(result.totalShops()).isEqualTo(8);
        assertThat(result.pendingShops()).isEqualTo(2);
        assertThat(result.approvedShops()).isEqualTo(5);

        assertThat(result.totalOrders()).isEqualTo(17);
        assertThat(result.pendingOrders()).isEqualTo(4);
        assertThat(result.confirmedOrders()).isEqualTo(3);
        assertThat(result.shippingOrders()).isEqualTo(2);
        assertThat(result.deliveredOrders()).isEqualTo(7);
        assertThat(result.cancelledOrders()).isEqualTo(1);
        assertThat(result.grossMerchandiseValue()).isEqualTo(1_600_000L);
        assertThat(result.collectedRevenue()).isEqualTo(700_000L);
    }

    @Test
    void missingStatusCountsAsZero() {
        when(orders.countByStatus()).thenReturn(Map.of(OrderStatus.DELIVERED, 2L));

        var result = stats.get();

        assertThat(result.totalOrders()).isEqualTo(2);
        assertThat(result.pendingOrders()).isZero();
        assertThat(result.cancelledOrders()).isZero();
    }

    @Test
    void trendCoversFourteenDaysEndingTodayInVietnamTime() {
        var result = stats.get();

        assertThat(result.dailyTrend()).hasSize(AdminStatsService.TREND_DAYS);
        assertThat(result.dailyTrend().get(0).date()).isEqualTo(TODAY.minusDays(13));
        assertThat(result.dailyTrend().get(13).date()).isEqualTo(TODAY);

        var expectedFrom = Instant.parse("2026-10-02T17:00:00Z");
        verify(orders).totalsPerDay(eq(expectedFrom), eq(AdminStatsService.ZONE));
        verify(users).countCreatedPerDay(eq(expectedFrom), eq(AdminStatsService.ZONE));
        verify(shops).countCreatedPerDay(eq(expectedFrom), eq(AdminStatsService.ZONE));
    }

    @Test
    void trendFillsMissingDaysWithZero() {
        when(users.countCreatedPerDay(any(), any())).thenReturn(Map.of(TODAY, 3L));
        when(shops.countCreatedPerDay(any(), any())).thenReturn(Map.of(TODAY.minusDays(1), 1L));
        when(orders.totalsPerDay(any(), any())).thenReturn(Map.of(TODAY, new DailyOrderTotals(2, 450_000L)));

        var trend = stats.get().dailyTrend();

        var today = trend.get(13);
        assertThat(today.newUsers()).isEqualTo(3);
        assertThat(today.newShops()).isZero();
        assertThat(today.orders()).isEqualTo(2);
        assertThat(today.orderValue()).isEqualTo(450_000L);

        var yesterday = trend.get(12);
        assertThat(yesterday.newShops()).isEqualTo(1);
        assertThat(yesterday.orders()).isZero();
        assertThat(yesterday.orderValue()).isZero();
    }
}
