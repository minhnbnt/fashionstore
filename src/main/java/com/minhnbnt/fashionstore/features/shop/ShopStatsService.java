package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.AlertResponse;
import com.minhnbnt.fashionstore.dto.shop.CustomerStatsResponse;
import com.minhnbnt.fashionstore.dto.shop.FollowerTrendResponse;
import com.minhnbnt.fashionstore.dto.shop.OrderStatusSummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.PeriodComparisonResponse;
import com.minhnbnt.fashionstore.dto.shop.ProductStatsResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueByCategoryResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueByPaymentResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopDashboardResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopPerformanceResponse;
import com.minhnbnt.fashionstore.dto.shop.TopCustomerResponse;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import com.minhnbnt.fashionstore.features.shop.excel.SellerExcelReportBuilder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopStatsService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ShopStatsRepository repository;

    private final ShopVisitLogRepository visitLog;

    private final ShopAccessChecker access;

    private final ShopRepository shopRepository;

    private final SellerExcelReportBuilder reportBuilder;

    @Transactional(readOnly = true)
    public ShopDashboardResponse getDashboard(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var stats = repository.todayStats(shopId);
        var pending = repository.countPendingOrders(shopId);
        var todayStart = LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toInstant();
        var visits = visitLog.countByShopIdAndVisitedAtAfter(shopId, todayStart);

        return new ShopDashboardResponse(
            stats.todayRevenue(),
            stats.todayOrders(),
            pending,
            visits,
            stats.yesterdayRevenue(),
            stats.yesterdayOrders()
        );
    }

    @Transactional(readOnly = true)
    public ShopPerformanceResponse getPerformance(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var perf = repository.shopPerformance(shopId);
        var chatResponseRate = 0.95; // Mặc định mục tiêu phản hồi CSKH

        return new ShopPerformanceResponse(
            chatResponseRate,
            perf.cancelRate(),
            perf.onTimeDeliveryRate(),
            perf.avgRating()
        );
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var alerts = new ArrayList<AlertResponse>();

        var pendingCount = repository.countPendingOrders(shopId);
        if (pendingCount > 0) {
            alerts.add(new AlertResponse(
                "PENDING_ORDER",
                "Có " + pendingCount + " đơn hàng đang chờ xác nhận",
                (int) pendingCount,
                "WARNING"
            ));
        }

        var outOfStockList = repository.outOfStockProducts(shopId);
        if (!outOfStockList.isEmpty()) {
            alerts.add(new AlertResponse(
                "OUT_OF_STOCK",
                outOfStockList.size() + " sản phẩm đã hết hàng trong kho",
                outOfStockList.size(),
                "DANGER"
            ));
        }

        return alerts;
    }

    @Transactional(readOnly = true)
    public PeriodComparisonResponse getPeriodComparison(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull String period
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var now = Instant.now();
        var from = now.minus(60, ChronoUnit.DAYS);
        var rows = repository.revenueByPeriod(shopId, period, from, now);

        var currentRevenue = 0L;
        var previousRevenue = 0L;
        var currentOrders = 0L;
        var previousOrders = 0L;

        if (!rows.isEmpty()) {
            var last = rows.get(rows.size() - 1);
            currentRevenue = last.revenue();
            currentOrders = last.orders();

            if (rows.size() >= 2) {
                var prev = rows.get(rows.size() - 2);
                previousRevenue = prev.revenue();
                previousOrders = prev.orders();
            }
        }

        var changePercent = 0.0;
        if (previousRevenue > 0) {
            changePercent = (double) (currentRevenue - previousRevenue) / previousRevenue;
        }

        return new PeriodComparisonResponse(
            currentRevenue,
            previousRevenue,
            changePercent,
            currentOrders,
            previousOrders
        );
    }

    @Transactional(readOnly = true)
    public RevenueDetailResponse getRevenueDetail(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull String period,
        @Nullable LocalDate fromDate,
        @Nullable LocalDate toDate
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var from = calculateFromInstant(fromDate);
        var to = calculateToInstant(toDate);

        var rows = repository.revenueByPeriod(shopId, period, from, to);
        var points = rows.stream()
            .map(r -> new RevenueDetailResponse.Point(r.periodLabel(), r.revenue(), r.orders()))
            .toList();

        return new RevenueDetailResponse(period, points);
    }

    @Transactional(readOnly = true)
    public List<RevenueByCategoryResponse> getRevenueByCategory(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        return repository.revenueByCategory(shopId).stream()
            .map(r -> new RevenueByCategoryResponse(r.categoryId(), r.categoryName(), r.revenue(), r.quantity()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RevenueByPaymentResponse> getRevenueByPayment(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        return repository.revenueByPayment(shopId).stream()
            .map(r -> new RevenueByPaymentResponse(r.paymentMethod(), r.revenue(), r.orderCount()))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderStatusSummaryResponse getOrderStatusSummary(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable LocalDate fromDate,
        @Nullable LocalDate toDate
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var from = calculateFromInstant(fromDate);
        var to = calculateToInstant(toDate);

        var r = repository.orderStatusSummary(shopId, from, to);
        return new OrderStatusSummaryResponse(
            r.completedOrders(),
            r.completedValue(),
            r.cancelledOrders(),
            r.cancelledValue(),
            r.returnedOrders(),
            r.returnedValue()
        );
    }

    @Transactional(readOnly = true)
    public List<ProductStatsResponse> getTopSellingProducts(@NonNull UUID userId, @NonNull UUID shopId, int limit) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var max = Math.clamp(limit, 1, 50);
        return repository.topSellingProducts(shopId, max).stream()
            .map(this::mapProductStats)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductStatsResponse> getLowConversionProducts(@NonNull UUID userId, @NonNull UUID shopId, int limit) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var max = Math.clamp(limit, 1, 50);
        return repository.lowConversionProducts(shopId, max).stream()
            .map(this::mapProductStats)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductStatsResponse> getOutOfStockProducts(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        return repository.outOfStockProducts(shopId).stream()
            .map(this::mapProductStats)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductStatsResponse> getSlowMovingProducts(@NonNull UUID userId, @NonNull UUID shopId, int limit) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var max = Math.clamp(limit, 1, 50);
        return repository.slowMovingProducts(shopId, max).stream()
            .map(this::mapProductStats)
            .toList();
    }

    @Transactional(readOnly = true)
    public CustomerStatsResponse getCustomerOverview(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var overview = repository.customerOverview(shopId);
        var followers = repository.followerTrend(shopId);

        var retentionRate = 0.0;
        if (overview.totalCustomers() > 0) {
            retentionRate = (double) overview.returningCustomers() / overview.totalCustomers();
        }

        var followerChange = followers.totalFollowers() - followers.previousMonthFollowers();

        return new CustomerStatsResponse(
            overview.totalCustomers(),
            overview.newCustomers(),
            overview.returningCustomers(),
            retentionRate,
            overview.avgOrderValue(),
            followers.totalFollowers(),
            followerChange
        );
    }

    @Transactional(readOnly = true)
    public List<TopCustomerResponse> getTopCustomers(@NonNull UUID userId, @NonNull UUID shopId, int limit) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var max = Math.clamp(limit, 1, 50);
        return repository.topCustomers(shopId, max).stream()
            .map(r -> new TopCustomerResponse(
                r.buyerId(),
                r.buyerName(),
                r.totalSpent(),
                (int) r.orderCount(),
                r.lastOrderAt().toString()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public FollowerTrendResponse getFollowerTrend(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var followers = repository.followerTrend(shopId);
        var change = followers.totalFollowers() - followers.previousMonthFollowers();
        return new FollowerTrendResponse(followers.totalFollowers(), change);
    }

    @Transactional(readOnly = true)
    public byte[] exportRevenueReport(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull String format,
        @Nullable LocalDate fromDate,
        @Nullable LocalDate toDate
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var from = calculateFromInstant(fromDate);
        var to = calculateToInstant(toDate);

        var rows = repository.revenueByPeriod(shopId, "day", from, to);

        if ("csv".equalsIgnoreCase(format)) {
            return reportBuilder.buildCsvReport(rows);
        }

        var shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin gian hàng"));

        var summary = repository.orderStatusSummary(shopId, from, to);
        var payments = repository.revenueByPayment(shopId, from, to);
        var products = repository.productPerformanceForExport(shopId, from, to);
        var orders = repository.ordersForExport(shopId, from, to);

        var startLocalDate = LocalDate.now(VN_ZONE).minusDays(30);
        if (fromDate != null) {
            startLocalDate = fromDate;
        }

        var endLocalDate = LocalDate.now(VN_ZONE);
        if (toDate != null) {
            endLocalDate = toDate;
        }

        return reportBuilder.buildExcelReport(
            shop.getName(),
            shop.getSlug(),
            startLocalDate,
            endLocalDate,
            summary,
            rows,
            payments,
            products,
            orders,
            true
        );
    }

    private ProductStatsResponse mapProductStats(ShopStatsRepository.ProductStatsRow row) {
        var conversionRate = 0.0;
        if (row.viewCount() > 0) {
            conversionRate = (double) row.totalSold() / row.viewCount();
        }

        return new ProductStatsResponse(
            row.productId(),
            row.productName(),
            row.defaultImageKey(),
            row.totalSold(),
            row.totalRevenue(),
            row.viewCount(),
            conversionRate,
            row.currentStock()
        );
    }

    private Instant calculateFromInstant(@Nullable LocalDate date) {
        if (date != null) {
            return date.atStartOfDay(VN_ZONE).toInstant();
        }
        return LocalDate.now(VN_ZONE).minusDays(30).atStartOfDay(VN_ZONE).toInstant();
    }

    private Instant calculateToInstant(@Nullable LocalDate date) {
        if (date != null) {
            return date.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
        }
        return LocalDate.now(VN_ZONE).plusDays(1).atStartOfDay(VN_ZONE).toInstant();
    }
}
