package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.AdminDailyStatResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminStatsResponse;
import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
import com.minhnbnt.fashionstore.features.catalog.ProductReportService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

    static final int TREND_DAYS = 14;

    private static final DailyOrderTotals NO_ORDERS = new DailyOrderTotals(0, 0);

    private final ShopService shops;

    private final UserService users;

    private final OrderService orders;

    private final ProductReportService productReports;

    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminStatsResponse get() {
        var shopCounts = shops.countByStatus();
        var userCounts = users.countByStatus();
        var roleCounts = users.countByRole();
        var orderCounts = orders.countByStatus();
        var revenue = orders.revenueTotals();

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
            dailyTrend()
        );
    }

    private List<AdminDailyStatResponse> dailyTrend() {
        var today = clock.instant().atZone(ZONE).toLocalDate();
        var firstDay = today.minusDays(TREND_DAYS - 1);
        var from = firstDay.atStartOfDay(ZONE).toInstant();

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
