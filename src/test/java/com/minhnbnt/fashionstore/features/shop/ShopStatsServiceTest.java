package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopStatsServiceTest {

    @Mock
    private ShopStatsRepository repository;

    @Mock
    private ShopVisitLogRepository visitLog;

    @Mock
    private ShopAccessChecker access;

    private ShopStatsService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID shopId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShopStatsService(repository, visitLog, access);
    }

    @Test
    void getDashboardReturnsTodayStats() {
        when(repository.todayStats(shopId))
            .thenReturn(new ShopStatsRepository.TodayStatsRow(100000L, 5L, 80000L, 3L));
        when(repository.countPendingOrders(shopId)).thenReturn(2L);
        when(visitLog.countByShopIdAndVisitedAtAfter(eq(shopId), any(Instant.class))).thenReturn(50L);

        var result = service.getDashboard(userId, shopId);

        assertThat(result.todayRevenue()).isEqualTo(100000L);
        assertThat(result.todayOrders()).isEqualTo(5L);
        assertThat(result.yesterdayRevenue()).isEqualTo(80000L);
        assertThat(result.yesterdayOrders()).isEqualTo(3L);
        assertThat(result.pendingOrders()).isEqualTo(2L);
        assertThat(result.shopVisits()).isEqualTo(50L);

        verify(access).requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);
    }

    @Test
    void getPerformanceCalculatesRatesCorrectly() {
        when(repository.shopPerformance(shopId))
            .thenReturn(new ShopStatsRepository.ShopPerformanceRow(0.05, 0.98, 4.8));

        var result = service.getPerformance(userId, shopId);

        assertThat(result.cancelRate()).isEqualTo(0.05);
        assertThat(result.onTimeDeliveryRate()).isEqualTo(0.98);
        assertThat(result.avgRating()).isEqualTo(4.8);
        assertThat(result.chatResponseRate()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void getAlertsIncludesOutOfStockWhenPresent() {
        when(repository.countPendingOrders(shopId)).thenReturn(3L);
        when(repository.outOfStockProducts(shopId)).thenReturn(List.of(
            new ShopStatsRepository.ProductStatsRow(UUID.randomUUID(), "P1", null, 10, 200000, 50, 0),
            new ShopStatsRepository.ProductStatsRow(UUID.randomUUID(), "P2", null, 5, 100000, 30, 0)
        ));

        var alerts = service.getAlerts(userId, shopId);

        assertThat(alerts).anyMatch(a -> "OUT_OF_STOCK".equals(a.type()) && a.count() == 2);
        assertThat(alerts).anyMatch(a -> "PENDING_ORDER".equals(a.type()) && a.count() == 3);
    }

    @Test
    void getPeriodComparisonCalculatesChangePercent() {
        var now = Instant.now();
        when(repository.revenueByPeriod(eq(shopId), eq("day"), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(
                new ShopStatsRepository.RevenuePeriodRow("2026-10-23", 100000L, 5L),
                new ShopStatsRepository.RevenuePeriodRow("2026-10-24", 120000L, 6L)
            ));

        var comparison = service.getPeriodComparison(userId, shopId, "day");

        assertThat(comparison.currentRevenue()).isEqualTo(120000L);
        assertThat(comparison.previousRevenue()).isEqualTo(100000L);
        assertThat(comparison.changePercent()).isCloseTo(0.20, offset(0.001));
    }

    @Test
    void getTopCustomersReturnsMappedList() {
        var bId = UUID.randomUUID();
        var lastOrder = Instant.now();
        when(repository.topCustomers(shopId, 5)).thenReturn(List.of(
            new ShopStatsRepository.TopCustomerRow(bId, "Alice", 500000L, 3L, lastOrder)
        ));

        var top = service.getTopCustomers(userId, shopId, 5);

        assertThat(top).hasSize(1);
        assertThat(top.get(0).buyerId()).isEqualTo(bId);
        assertThat(top.get(0).buyerName()).isEqualTo("Alice");
        assertThat(top.get(0).totalSpent()).isEqualTo(500000L);
        assertThat(top.get(0).orderCount()).isEqualTo(3);
    }
}
