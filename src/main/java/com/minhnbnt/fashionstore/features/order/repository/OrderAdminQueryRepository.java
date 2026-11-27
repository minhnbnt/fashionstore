package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.dto.admin.AdminTopCategoryResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminTopProductResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminTopShopResponse;
import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
import com.minhnbnt.fashionstore.dto.admin.FulfillmentTotals;
import com.minhnbnt.fashionstore.dto.admin.OrderRevenueTotals;
import com.minhnbnt.fashionstore.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read model số liệu đơn hàng cho dashboard admin.
 */
@Repository
@RequiredArgsConstructor
public class OrderAdminQueryRepository {

    static final String UNCATEGORIZED = "Chưa phân loại";

    private final JdbcClient jdbc;

    public Map<OrderStatus, Long> countByStatus() {
        var counts = new EnumMap<OrderStatus, Long>(OrderStatus.class);
        for (var status : OrderStatus.values()) {
            counts.put(status, 0L);
        }

        var rows = jdbc.sql("SELECT status, count(*) AS total FROM orders GROUP BY status")
            .query((rs, rowNum) -> Map.entry(OrderStatus.valueOf(rs.getString("status")), rs.getLong("total")))
            .list();
        for (var row : rows) {
            counts.put(row.getKey(), row.getValue());
        }

        return counts;
    }

    public OrderRevenueTotals revenueTotals() {
        var sql = """
            SELECT coalesce(sum(total) FILTER (WHERE status <> 'CANCELLED'), 0) AS gmv,
                   coalesce(sum(total) FILTER (WHERE payment_status = 'COLLECTED'), 0) AS collected
            FROM orders
            """;

        return jdbc.sql(sql)
            .query((rs, rowNum) -> new OrderRevenueTotals(rs.getLong("gmv"), rs.getLong("collected")))
            .single();
    }

    /**
     * Gom đơn theo ngày đặt (tính theo {@code zone}) từ {@code from} đến nay. Ngày không có đơn thì không có key.
     */
    public Map<LocalDate, DailyOrderTotals> totalsPerDay(Instant from, ZoneId zone) {
        var sql = """
            SELECT (created_at AT TIME ZONE :zone)::date AS day,
                   count(*) AS orders,
                   coalesce(sum(total) FILTER (WHERE status <> 'CANCELLED'), 0) AS value
            FROM orders
            WHERE created_at >= :from
            GROUP BY day
            """;

        var rows = jdbc.sql(sql)
            .param("zone", zone.getId())
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .query((rs, rowNum) -> Map.entry(
                rs.getObject("day", LocalDate.class),
                new DailyOrderTotals(rs.getLong("orders"), rs.getLong("value"))
            ))
            .list();

        var result = new HashMap<LocalDate, DailyOrderTotals>();
        for (var row : rows) {
            result.put(row.getKey(), row.getValue());
        }

        return result;
    }

    /**
     * Shop xếp theo GMV đơn chưa hủy đặt từ {@code from}, cao trước.
     */
    public List<AdminTopShopResponse> topShops(Instant from, int limit) {
        var sql = """
            SELECT s.id, s.name, s.slug, count(*) AS orders, sum(o.total) AS gmv
            FROM orders o
            JOIN shop s ON s.id = o.shop_id
            WHERE o.status <> 'CANCELLED' AND o.created_at >= :from
            GROUP BY s.id, s.name, s.slug
            ORDER BY gmv DESC, orders DESC, s.name
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .param("limit", limit)
            .query((rs, rowNum) -> new AdminTopShopResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getLong("orders"),
                rs.getLong("gmv")
            ))
            .list();
    }

    /**
     * Sản phẩm xếp theo số lượng bán trong đơn chưa hủy đặt từ {@code from}. Dòng hàng mà sản phẩm
     * đã bị xóa ({@code product_id} null) không tính.
     */
    public List<AdminTopProductResponse> topProducts(Instant from, int limit) {
        var sql = """
            SELECT p.id, p.name, s.name AS shop_name,
                   sum(oi.quantity) AS quantity, sum(oi.line_total) AS revenue
            FROM order_item oi
            JOIN orders o ON o.id = oi.order_id
            JOIN product p ON p.id = oi.product_id
            JOIN shop s ON s.id = o.shop_id
            WHERE o.status <> 'CANCELLED' AND o.created_at >= :from
            GROUP BY p.id, p.name, s.name
            ORDER BY quantity DESC, revenue DESC, p.name
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .param("limit", limit)
            .query((rs, rowNum) -> new AdminTopProductResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("shop_name"),
                rs.getLong("quantity"),
                rs.getLong("revenue")
            ))
            .list();
    }

    /**
     * Danh mục xếp theo doanh số dòng hàng; sản phẩm chưa gắn danh mục gom chung một nhóm.
     */
    public List<AdminTopCategoryResponse> topCategories(Instant from, int limit) {
        var sql = """
            SELECT coalesce(c.name, :uncategorized) AS name,
                   sum(oi.quantity) AS quantity, sum(oi.line_total) AS revenue
            FROM order_item oi
            JOIN orders o ON o.id = oi.order_id
            LEFT JOIN product p ON p.id = oi.product_id
            LEFT JOIN category c ON c.id = p.category_id
            WHERE o.status <> 'CANCELLED' AND o.created_at >= :from
            GROUP BY c.id, c.name
            ORDER BY revenue DESC, quantity DESC, name
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("uncategorized", UNCATEGORIZED)
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .param("limit", limit)
            .query((rs, rowNum) -> new AdminTopCategoryResponse(
                rs.getString("name"),
                rs.getLong("quantity"),
                rs.getLong("revenue")
            ))
            .list();
    }

    /**
     * Giờ xử lý trung bình (đặt → xác nhận, đặt → giao) và số người mua / người mua lại,
     * tính trên đơn đặt từ {@code from}.
     */
    public FulfillmentTotals fulfillment(Instant from) {
        var sql = """
            WITH recent AS (
                SELECT * FROM orders WHERE created_at >= :from
            ),
            buyers AS (
                SELECT buyer_id, count(*) AS placed
                FROM recent
                WHERE status <> 'CANCELLED'
                GROUP BY buyer_id
            )
            SELECT
                coalesce(
                    (SELECT avg(extract(epoch FROM confirmed_at - created_at)) FROM recent
                     WHERE confirmed_at IS NOT NULL),
                    0
                ) / 3600.0 AS confirm_hours,
                coalesce(
                    (SELECT avg(extract(epoch FROM delivered_at - created_at)) FROM recent
                     WHERE delivered_at IS NOT NULL),
                    0
                ) / 3600.0 AS delivery_hours,
                (SELECT count(*) FROM buyers) AS active_buyers,
                (SELECT count(*) FROM buyers WHERE placed >= 2) AS repeat_buyers
            """;

        return jdbc.sql(sql)
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .query((rs, rowNum) -> new FulfillmentTotals(
                rs.getDouble("confirm_hours"),
                rs.getDouble("delivery_hours"),
                rs.getLong("active_buyers"),
                rs.getLong("repeat_buyers")
            ))
            .single();
    }

    public long countOpenReturns() {
        return jdbc.sql("SELECT count(*) FROM return_request WHERE status = 'REQUESTED'")
            .query(Long.class)
            .single();
    }
}
