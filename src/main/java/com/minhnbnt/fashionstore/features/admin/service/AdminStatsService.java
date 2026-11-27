package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.AdminDailyStatResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminStatsResponse;
import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    /**
     * Sàn bán ở Việt Nam: "hôm nay" và mốc ngày của biểu đồ tính theo giờ Việt Nam.
     */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * 60 ngày để client so sánh N ngày gần nhất với N ngày liền trước (N tối đa 30).
     */
    static final int TREND_DAYS = 60;

    /**
     * Cửa sổ của bảng xếp hạng, tốc độ xử lý đơn và người mua lại.
     */
    static final int WINDOW_DAYS = 30;

    static final int TOP_LIMIT = 5;

    private static final DailyOrderTotals NO_ORDERS = new DailyOrderTotals(0, 0);

    private final ShopService shops;

    private final UserService users;

    private final OrderService orders;

    private final ProductReportService productReports;

    private final CatalogService catalog;

    private final ReviewService reviews;

    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminStatsResponse get() {
        var shopCounts = shops.countByStatus();
        var userCounts = users.countByStatus();
        var roleCounts = users.countByRole();
        var orderCounts = orders.countByStatus();
        var productCounts = catalog.countProductsByStatus();
        var revenue = orders.revenueTotals();
        var reviewTotals = reviews.platformTotals();

        var windowFrom = startOfDayAgo(WINDOW_DAYS - 1);
        var fulfillment = orders.fulfillment(windowFrom);

        return new AdminStatsResponse(
            sum(userCounts),
            countOf(userCounts, AccountStatus.LOCKED),
            countOf(roleCounts, UserRole.CUSTOMER),
            countOf(roleCounts, UserRole.SHOP),
            countOf(roleCounts, UserRole.ADMIN),
            sum(shopCounts),
            countOf(shopCounts, ShopStatus.PENDING),
            countOf(shopCounts, ShopStatus.APPROVED),
            countOf(shopCounts, ShopStatus.SUSPENDED),
            countOf(shopCounts, ShopStatus.REJECTED),
            sum(orderCounts),
            countOf(orderCounts, OrderStatus.PENDING),
            countOf(orderCounts, OrderStatus.CONFIRMED),
            countOf(orderCounts, OrderStatus.SHIPPING),
            countOf(orderCounts, OrderStatus.DELIVERED),
            countOf(orderCounts, OrderStatus.CANCELLED),
            revenue.grossMerchandiseValue(),
            revenue.collected(),
            productReports.countOpen(),
            sum(productCounts),
            countOf(productCounts, ProductStatus.ACTIVE),
            countOf(productCounts, ProductStatus.PENDING),
            countOf(productCounts, ProductStatus.HIDDEN),
            countOf(productCounts, ProductStatus.BANNED),
            reviewTotals.count(),
            reviewTotals.averageRating(),
            orders.countOpenReturns(),
            fulfillment.avgConfirmHours(),
            fulfillment.avgDeliveryHours(),
            fulfillment.activeBuyers(),
            fulfillment.repeatBuyers(),
            orders.topShops(windowFrom, TOP_LIMIT),
            orders.topProducts(windowFrom, TOP_LIMIT),
            orders.topCategories(windowFrom, TOP_LIMIT),
            dailyTrend()
        );
    }

    private LocalDate today() {
        return clock.instant().atZone(ZONE).toLocalDate();
    }

    /**
     * 00:00 giờ Việt Nam của ngày cách hôm nay {@code days} ngày.
     */
    private Instant startOfDayAgo(int days) {
        return today().minusDays(days).atStartOfDay(ZONE).toInstant();
    }

    private List<AdminDailyStatResponse> dailyTrend() {
        var firstDay = today().minusDays(TREND_DAYS - 1);
        var from = startOfDayAgo(TREND_DAYS - 1);

        var newUsers = users.countCreatedPerDay(from, ZONE);
        var newShops = shops.countCreatedPerDay(from, ZONE);
        var orderTotals = orders.totalsPerDay(from, ZONE);

        var trend = new ArrayList<AdminDailyStatResponse>();
        for (var offset = 0; offset < TREND_DAYS; offset++) {
            var day = firstDay.plusDays(offset);
            var dayOrders = orderTotalsOf(orderTotals, day);

            trend.add(new AdminDailyStatResponse(
                day,
                countOf(newUsers, day),
                countOf(newShops, day),
                dayOrders.orders(),
                dayOrders.value()
            ));
        }

        return trend;
    }

    private static <K> long sum(Map<K, Long> counts) {
        long total = 0;
        for (var count : counts.values()) {
            total += count;
        }
        return total;
    }

    private static <K> long countOf(Map<K, Long> counts, K key) {
        return Objects.requireNonNullElse(counts.get(key), 0L);
    }

    private static DailyOrderTotals orderTotalsOf(Map<LocalDate, DailyOrderTotals> totals, LocalDate day) {
        return Objects.requireNonNullElse(totals.get(day), NO_ORDERS);
    }
}
