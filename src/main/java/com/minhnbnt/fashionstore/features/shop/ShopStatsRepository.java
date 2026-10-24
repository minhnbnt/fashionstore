package com.minhnbnt.fashionstore.features.shop;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ShopStatsRepository {

    private final JdbcClient jdbc;

    public record TodayStatsRow(long todayRevenue, long todayOrders, long yesterdayRevenue, long yesterdayOrders) {}

    public record ShopPerformanceRow(double cancelRate, double onTimeDeliveryRate, double avgRating) {}

    public record RevenuePeriodRow(String periodLabel, long revenue, long orders) {}

    public record RevenueByCategoryRow(UUID categoryId, String categoryName, long revenue, long quantity) {}

    public record RevenueByPaymentRow(String paymentMethod, long revenue, long orderCount) {}

    public record OrderStatusSummaryRow(long completedOrders, long completedValue, long cancelledOrders, long cancelledValue, long returnedOrders, long returnedValue) {}

    public record ProductStatsRow(UUID productId, String productName, @Nullable String defaultImageKey, long totalSold, long totalRevenue, long viewCount, int currentStock) {}

    public record CustomerOverviewRow(long totalCustomers, long newCustomers, long returningCustomers, long avgOrderValue) {}

    public record TopCustomerRow(UUID buyerId, String buyerName, long totalSpent, long orderCount, Instant lastOrderAt) {}

    public record FollowerTrendRow(long totalFollowers, long previousMonthFollowers) {}

    public TodayStatsRow todayStats(UUID shopId) {
        var sql = """
            SELECT
                COALESCE(SUM(CASE WHEN created_at >= CURRENT_DATE AND created_at < CURRENT_DATE + INTERVAL '1 day' AND status != 'CANCELLED' THEN total ELSE 0 END), 0) AS today_revenue,
                COALESCE(COUNT(CASE WHEN created_at >= CURRENT_DATE AND created_at < CURRENT_DATE + INTERVAL '1 day' AND status != 'CANCELLED' THEN 1 END), 0) AS today_orders,
                COALESCE(SUM(CASE WHEN created_at >= CURRENT_DATE - INTERVAL '1 day' AND created_at < CURRENT_DATE AND status != 'CANCELLED' THEN total ELSE 0 END), 0) AS yesterday_revenue,
                COALESCE(COUNT(CASE WHEN created_at >= CURRENT_DATE - INTERVAL '1 day' AND created_at < CURRENT_DATE AND status != 'CANCELLED' THEN 1 END), 0) AS yesterday_orders
            FROM orders
            WHERE shop_id = :shopId
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new TodayStatsRow(
                rs.getLong("today_revenue"),
                rs.getLong("today_orders"),
                rs.getLong("yesterday_revenue"),
                rs.getLong("yesterday_orders")
            ))
            .single();
    }

    public long countPendingOrders(UUID shopId) {
        var sql = "SELECT count(*) FROM orders WHERE shop_id = :shopId AND status = 'PENDING'";
        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query(Long.class)
            .single();
    }

    public ShopPerformanceRow shopPerformance(UUID shopId) {
        var orderSql = """
            SELECT
                COUNT(*) AS total_orders,
                COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_orders,
                COUNT(CASE WHEN status = 'DELIVERED' THEN 1 END) AS delivered_orders,
                COUNT(CASE WHEN status = 'DELIVERED' AND (shipped_at IS NULL OR delivered_at IS NULL OR delivered_at <= shipped_at + INTERVAL '7 days') THEN 1 END) AS on_time_orders
            FROM orders
            WHERE shop_id = :shopId
            """;

        record OrderMetrics(long total, long cancelled, long delivered, long onTime) {}

        var metrics = jdbc.sql(orderSql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new OrderMetrics(
                rs.getLong("total_orders"),
                rs.getLong("cancelled_orders"),
                rs.getLong("delivered_orders"),
                rs.getLong("on_time_orders")
            ))
            .single();

        var ratingSql = """
            SELECT COALESCE(AVG(r.stars), 5.0)
            FROM review r
            JOIN product p ON r.product_id = p.id
            WHERE p.shop_id = :shopId
            """;

        var avgRating = jdbc.sql(ratingSql)
            .param("shopId", shopId)
            .query(Double.class)
            .single();

        var cancelRate = 0.0;
        if (metrics.total() > 0) {
            cancelRate = (double) metrics.cancelled() / metrics.total();
        }

        var onTimeRate = 1.0;
        if (metrics.delivered() > 0) {
            onTimeRate = (double) metrics.onTime() / metrics.delivered();
        }

        return new ShopPerformanceRow(cancelRate, onTimeRate, avgRating);
    }

    public List<RevenuePeriodRow> revenueByPeriod(UUID shopId, String period, Instant from, Instant to) {
        String formatExpr;
        if ("week".equalsIgnoreCase(period)) {
            formatExpr = "to_char(date_trunc('week', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'YYYY-\"W\"IW')";
        } else if ("month".equalsIgnoreCase(period)) {
            formatExpr = "to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM')";
        } else if ("year".equalsIgnoreCase(period)) {
            formatExpr = "to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY')";
        } else {
            formatExpr = "to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD')";
        }

        var sql = """
            SELECT
                """ + formatExpr + """
                AS period_label,
                COALESCE(SUM(total), 0) AS revenue,
                COUNT(id) AS orders
            FROM orders
            WHERE shop_id = :shopId
              AND status != 'CANCELLED'
              AND created_at >= :from
              AND created_at < :to
            GROUP BY period_label
            ORDER BY period_label ASC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("from", java.sql.Timestamp.from(from))
            .param("to", java.sql.Timestamp.from(to))
            .query((rs, rowNum) -> new RevenuePeriodRow(
                rs.getString("period_label"),
                rs.getLong("revenue"),
                rs.getLong("orders")
            ))
            .list();
    }

    public List<RevenueByCategoryRow> revenueByCategory(UUID shopId) {
        var sql = """
            SELECT
                c.id AS category_id,
                c.name AS category_name,
                COALESCE(SUM(oi.line_total), 0) AS revenue,
                COALESCE(SUM(oi.quantity), 0) AS quantity
            FROM order_item oi
            JOIN orders o ON oi.order_id = o.id
            JOIN product p ON oi.product_id = p.id
            JOIN category c ON p.category_id = c.id
            WHERE o.shop_id = :shopId
              AND o.status != 'CANCELLED'
            GROUP BY c.id, c.name
            ORDER BY revenue DESC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new RevenueByCategoryRow(
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getLong("revenue"),
                rs.getLong("quantity")
            ))
            .list();
    }

    public List<RevenueByPaymentRow> revenueByPayment(UUID shopId) {
        var sql = """
            SELECT
                o.payment_method AS payment_method,
                COALESCE(SUM(o.total), 0) AS revenue,
                COUNT(o.id) AS order_count
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.status != 'CANCELLED'
            GROUP BY o.payment_method
            ORDER BY revenue DESC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new RevenueByPaymentRow(
                rs.getString("payment_method"),
                rs.getLong("revenue"),
                rs.getLong("order_count")
            ))
            .list();
    }

    public OrderStatusSummaryRow orderStatusSummary(UUID shopId, Instant from, Instant to) {
        var sql = """
            SELECT
                COALESCE(COUNT(CASE WHEN o.status = 'DELIVERED' THEN 1 END), 0) AS completed_orders,
                COALESCE(SUM(CASE WHEN o.status = 'DELIVERED' THEN o.total ELSE 0 END), 0) AS completed_value,
                COALESCE(COUNT(CASE WHEN o.status = 'CANCELLED' THEN 1 END), 0) AS cancelled_orders,
                COALESCE(SUM(CASE WHEN o.status = 'CANCELLED' THEN o.total ELSE 0 END), 0) AS cancelled_value,
                COALESCE(COUNT(rr.id), 0) AS returned_orders,
                COALESCE(SUM(CASE WHEN rr.id IS NOT NULL THEN COALESCE(rr.refund_amount, 0) ELSE 0 END), 0) AS returned_value
            FROM orders o
            LEFT JOIN return_request rr ON rr.order_id = o.id AND rr.status = 'APPROVED'
            WHERE o.shop_id = :shopId
              AND o.created_at >= :from
              AND o.created_at < :to
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("from", java.sql.Timestamp.from(from))
            .param("to", java.sql.Timestamp.from(to))
            .query((rs, rowNum) -> new OrderStatusSummaryRow(
                rs.getLong("completed_orders"),
                rs.getLong("completed_value"),
                rs.getLong("cancelled_orders"),
                rs.getLong("cancelled_value"),
                rs.getLong("returned_orders"),
                rs.getLong("returned_value")
            ))
            .single();
    }

    public List<ProductStatsRow> topSellingProducts(UUID shopId, int limit) {
        var sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                (SELECT pi.s3_key FROM product_image pi WHERE pi.product_id = p.id ORDER BY pi.sort_order ASC LIMIT 1) AS default_image_key,
                COALESCE(SUM(oi.quantity), 0) AS total_sold,
                COALESCE(SUM(oi.line_total), 0) AS total_revenue,
                p.view_count AS view_count,
                COALESCE((SELECT SUM(pv.stock_quantity) FROM product_variant pv WHERE pv.product_id = p.id), 0) AS current_stock
            FROM product p
            JOIN order_item oi ON oi.product_id = p.id
            JOIN orders o ON oi.order_id = o.id
            WHERE p.shop_id = :shopId
              AND o.status != 'CANCELLED'
            GROUP BY p.id, p.name, p.view_count
            ORDER BY total_revenue DESC
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("limit", limit)
            .query((rs, rowNum) -> new ProductStatsRow(
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("default_image_key"),
                rs.getLong("total_sold"),
                rs.getLong("total_revenue"),
                rs.getLong("view_count"),
                rs.getInt("current_stock")
            ))
            .list();
    }

    public List<ProductStatsRow> lowConversionProducts(UUID shopId, int limit) {
        var sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                (SELECT pi.s3_key FROM product_image pi WHERE pi.product_id = p.id ORDER BY pi.sort_order ASC LIMIT 1) AS default_image_key,
                COALESCE(SUM(oi.quantity), 0) AS total_sold,
                COALESCE(SUM(oi.line_total), 0) AS total_revenue,
                p.view_count AS view_count,
                COALESCE((SELECT SUM(pv.stock_quantity) FROM product_variant pv WHERE pv.product_id = p.id), 0) AS current_stock
            FROM product p
            LEFT JOIN order_item oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id AND o.status != 'CANCELLED'
            WHERE p.shop_id = :shopId
              AND p.status = 'ACTIVE'
              AND p.view_count > 0
            GROUP BY p.id, p.name, p.view_count
            ORDER BY (COALESCE(SUM(oi.quantity), 0)::float / p.view_count) ASC, p.view_count DESC
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("limit", limit)
            .query((rs, rowNum) -> new ProductStatsRow(
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("default_image_key"),
                rs.getLong("total_sold"),
                rs.getLong("total_revenue"),
                rs.getLong("view_count"),
                rs.getInt("current_stock")
            ))
            .list();
    }

    public List<ProductStatsRow> outOfStockProducts(UUID shopId) {
        var sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                (SELECT pi.s3_key FROM product_image pi WHERE pi.product_id = p.id ORDER BY pi.sort_order ASC LIMIT 1) AS default_image_key,
                COALESCE(SUM(oi.quantity), 0) AS total_sold,
                COALESCE(SUM(oi.line_total), 0) AS total_revenue,
                p.view_count AS view_count,
                COALESCE(SUM(pv.stock_quantity), 0) AS current_stock
            FROM product p
            JOIN product_variant pv ON pv.product_id = p.id
            LEFT JOIN order_item oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id AND o.status != 'CANCELLED'
            WHERE p.shop_id = :shopId
              AND p.status = 'ACTIVE'
            GROUP BY p.id, p.name, p.view_count
            HAVING COALESCE(SUM(pv.stock_quantity), 0) = 0
            ORDER BY p.name ASC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new ProductStatsRow(
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("default_image_key"),
                rs.getLong("total_sold"),
                rs.getLong("total_revenue"),
                rs.getLong("view_count"),
                rs.getInt("current_stock")
            ))
            .list();
    }

    public List<ProductStatsRow> slowMovingProducts(UUID shopId, int limit) {
        var sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                (SELECT pi.s3_key FROM product_image pi WHERE pi.product_id = p.id ORDER BY pi.sort_order ASC LIMIT 1) AS default_image_key,
                COALESCE(SUM(oi.quantity), 0) AS total_sold,
                COALESCE(SUM(oi.line_total), 0) AS total_revenue,
                p.view_count AS view_count,
                COALESCE((SELECT SUM(pv.stock_quantity) FROM product_variant pv WHERE pv.product_id = p.id), 0) AS current_stock
            FROM product p
            LEFT JOIN order_item oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id AND o.status != 'CANCELLED'
            WHERE p.shop_id = :shopId
              AND p.status = 'ACTIVE'
              AND COALESCE((SELECT SUM(pv.stock_quantity) FROM product_variant pv WHERE pv.product_id = p.id), 0) > 0
            GROUP BY p.id, p.name, p.view_count
            HAVING MAX(o.created_at) IS NULL OR MAX(o.created_at) < (NOW() - INTERVAL '30 days')
            ORDER BY current_stock DESC
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("limit", limit)
            .query((rs, rowNum) -> new ProductStatsRow(
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("default_image_key"),
                rs.getLong("total_sold"),
                rs.getLong("total_revenue"),
                rs.getLong("view_count"),
                rs.getInt("current_stock")
            ))
            .list();
    }

    public CustomerOverviewRow customerOverview(UUID shopId) {
        var sql = """
            WITH customer_orders AS (
                SELECT
                    buyer_id,
                    COUNT(id) AS order_count,
                    SUM(total) AS total_spent
                FROM orders
                WHERE shop_id = :shopId AND status != 'CANCELLED'
                GROUP BY buyer_id
            )
            SELECT
                COALESCE(COUNT(*), 0) AS total_customers,
                COALESCE(COUNT(CASE WHEN order_count = 1 THEN 1 END), 0) AS new_customers,
                COALESCE(COUNT(CASE WHEN order_count > 1 THEN 1 END), 0) AS returning_customers,
                COALESCE(AVG(total_spent / order_count), 0)::bigint AS avg_order_value
            FROM customer_orders
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new CustomerOverviewRow(
                rs.getLong("total_customers"),
                rs.getLong("new_customers"),
                rs.getLong("returning_customers"),
                rs.getLong("avg_order_value")
            ))
            .single();
    }

    public List<TopCustomerRow> topCustomers(UUID shopId, int limit) {
        var sql = """
            SELECT
                u.id AS buyer_id,
                u.display_name AS buyer_name,
                SUM(o.total) AS total_spent,
                COUNT(o.id) AS order_count,
                MAX(o.created_at) AS last_order_at
            FROM orders o
            JOIN user_profile u ON o.buyer_id = u.id
            WHERE o.shop_id = :shopId
              AND o.status != 'CANCELLED'
            GROUP BY u.id, u.display_name
            ORDER BY total_spent DESC, order_count DESC
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("limit", limit)
            .query((rs, rowNum) -> new TopCustomerRow(
                rs.getObject("buyer_id", UUID.class),
                rs.getString("buyer_name"),
                rs.getLong("total_spent"),
                rs.getLong("order_count"),
                rs.getTimestamp("last_order_at").toInstant()
            ))
            .list();
    }

    public FollowerTrendRow followerTrend(UUID shopId) {
        var sql = """
            SELECT
                COUNT(*) AS total_followers,
                COUNT(CASE WHEN created_at < date_trunc('month', NOW()) THEN 1 END) AS previous_month_followers
            FROM shop_follower
            WHERE shop_id = :shopId
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new FollowerTrendRow(
                rs.getLong("total_followers"),
                rs.getLong("previous_month_followers")
            ))
            .single();
    }
}
