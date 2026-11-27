package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.AdminTopCategoryResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminTopProductResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminTopShopResponse;
import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
import com.minhnbnt.fashionstore.dto.admin.FulfillmentTotals;
import com.minhnbnt.fashionstore.dto.admin.OrderRevenueTotals;
import com.minhnbnt.fashionstore.dto.admin.ReviewTotals;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.ProductReportService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private CatalogService catalog;

    private ReviewService reviews;

    private AdminStatsService stats;

    @BeforeEach
    void setUp() {
        shops = mock(ShopService.class);
        users = mock(UserService.class);
        orders = mock(OrderService.class);
        productReports = mock(ProductReportService.class);
        catalog = mock(CatalogService.class);
        reviews = mock(ReviewService.class);
        stats = new AdminStatsService(
            shops,
            users,
            orders,
            productReports,
            catalog,
            reviews,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

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
        when(orders.topShops(any(), eq(AdminStatsService.TOP_LIMIT))).thenReturn(List.of());
        when(orders.topProducts(any(), eq(AdminStatsService.TOP_LIMIT))).thenReturn(List.of());
        when(orders.topCategories(any(), eq(AdminStatsService.TOP_LIMIT))).thenReturn(List.of());
        when(orders.fulfillment(any())).thenReturn(new FulfillmentTotals(0, 0, 0, 0));
        when(orders.countOpenReturns()).thenReturn(0L);
        when(catalog.countProductsByStatus()).thenReturn(Map.of());
        when(reviews.platformTotals()).thenReturn(new ReviewTotals(0, 0));
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
    void trendCoversSixtyDaysEndingTodayInVietnamTime() {
        var result = stats.get();

        assertThat(AdminStatsService.TREND_DAYS).isEqualTo(60);
        assertThat(result.dailyTrend()).hasSize(AdminStatsService.TREND_DAYS);
        assertThat(result.dailyTrend().get(0).date()).isEqualTo(TODAY.minusDays(59));
        assertThat(result.dailyTrend().get(59).date()).isEqualTo(TODAY);

        // 2026-08-18 00:00 giờ Việt Nam
        var expectedFrom = Instant.parse("2026-08-17T17:00:00Z");
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

        var today = trend.get(59);
        assertThat(today.newUsers()).isEqualTo(3);
        assertThat(today.newShops()).isZero();
        assertThat(today.orders()).isEqualTo(2);
        assertThat(today.orderValue()).isEqualTo(450_000L);

        var yesterday = trend.get(58);
        assertThat(yesterday.newShops()).isEqualTo(1);
        assertThat(yesterday.orders()).isZero();
        assertThat(yesterday.orderValue()).isZero();
    }

    @Test
    void productCountsByStatusDefaultToZero() {
        when(catalog.countProductsByStatus()).thenReturn(Map.of(
            ProductStatus.ACTIVE, 12L,
            ProductStatus.PENDING, 3L,
            ProductStatus.BANNED, 1L
        ));

        var result = stats.get();

        assertThat(result.totalProducts()).isEqualTo(16);
        assertThat(result.activeProducts()).isEqualTo(12);
        assertThat(result.pendingProducts()).isEqualTo(3);
        assertThat(result.hiddenProducts()).isZero();
        assertThat(result.bannedProducts()).isEqualTo(1);
    }

    @Test
    void rankingsAndFulfillmentUseThirtyDayWindowInVietnamTime() {
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var topShop = new AdminTopShopResponse(shopId, "nét lab", "net-lab", 4, 900_000L);
        var topProduct = new AdminTopProductResponse(productId, "Áo thun", "nét lab", 7, 700_000L);
        var topCategory = new AdminTopCategoryResponse("Áo", 9, 800_000L);
        when(orders.topShops(any(), eq(AdminStatsService.TOP_LIMIT))).thenReturn(List.of(topShop));
        when(orders.topProducts(any(), eq(AdminStatsService.TOP_LIMIT))).thenReturn(List.of(topProduct));
        when(orders.topCategories(any(), eq(AdminStatsService.TOP_LIMIT))).thenReturn(List.of(topCategory));
        when(orders.fulfillment(any())).thenReturn(new FulfillmentTotals(5.5, 48.0, 20, 6));

        var result = stats.get();

        assertThat(result.topShops()).containsExactly(topShop);
        assertThat(result.topProducts()).containsExactly(topProduct);
        assertThat(result.topCategories()).containsExactly(topCategory);
        assertThat(result.avgConfirmHours()).isEqualTo(5.5);
        assertThat(result.avgDeliveryHours()).isEqualTo(48.0);
        assertThat(result.activeBuyers()).isEqualTo(20);
        assertThat(result.repeatBuyers()).isEqualTo(6);

        // 2026-09-17 00:00 giờ Việt Nam: 30 ngày tính cả hôm nay
        var windowFrom = Instant.parse("2026-09-16T17:00:00Z");
        verify(orders).topShops(eq(windowFrom), eq(AdminStatsService.TOP_LIMIT));
        verify(orders).topProducts(eq(windowFrom), eq(AdminStatsService.TOP_LIMIT));
        verify(orders).topCategories(eq(windowFrom), eq(AdminStatsService.TOP_LIMIT));
        verify(orders).fulfillment(eq(windowFrom));
    }

    @Test
    void reviewsAndReturnsComeFromTheirServices() {
        when(reviews.platformTotals()).thenReturn(new ReviewTotals(42, 4.35));
        when(orders.countOpenReturns()).thenReturn(3L);

        var result = stats.get();

        assertThat(result.totalReviews()).isEqualTo(42);
        assertThat(result.averageRating()).isEqualTo(4.35);
        assertThat(result.openReturnRequests()).isEqualTo(3);
    }
}
