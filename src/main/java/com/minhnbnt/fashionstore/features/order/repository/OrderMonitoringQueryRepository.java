package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderSummaryResponse;
import com.minhnbnt.fashionstore.dto.admin.OrderTimelineResponse;
import com.minhnbnt.fashionstore.infrastructure.SqlLike;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model màn giám sát đơn toàn sàn: đơn + shop + người mua + tổng hợp sản phẩm + cảnh báo SLA.
 */
@Repository
@RequiredArgsConstructor
public class OrderMonitoringQueryRepository {

    /** Shop phải xác nhận đơn trong 24 giờ. */
    static final Duration CONFIRM_SLA = Duration.ofHours(24);

    /** Shopee/Lazada: 2 ngày kể từ lúc đặt phải bàn giao cho vận chuyển. */
    static final Duration HANDOVER_SLA = Duration.ofHours(48);

    /** Lazada: hàng phải tới nơi trong 7 ngày kể từ lúc gửi. */
    static final Duration DELIVERY_SLA = Duration.ofDays(7);

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String ALERT = """
        CASE
            WHEN o.status = 'PENDING' AND o.created_at <= :confirmCutoff THEN 'CONFIRM_LATE'
            WHEN o.status = 'CONFIRMED' AND o.created_at <= :handoverCutoff THEN 'HANDOVER_LATE'
            WHEN o.status = 'SHIPPING' AND o.shipped_at <= :deliveryCutoff THEN 'DELIVERY_LATE'
            WHEN o.status = 'DELIVERED' AND ret.status IN ('REQUESTED', 'APPROVED') THEN 'RETURN_OPEN'
        END""";

    private static final String BASE_FROM = """
        FROM orders o
        JOIN shop s ON s.id = o.shop_id
        JOIN user_profile u ON u.id = o.buyer_id
        LEFT JOIN LATERAL (
            SELECT r.status
            FROM return_request r
            WHERE r.order_id = o.id
            ORDER BY r.created_at DESC, r.id
            LIMIT 1
        ) ret ON TRUE
        """;

    private static final String SELECT = """
        SELECT o.id, o.code, o.shop_id, s.name AS shop_name, s.slug AS shop_slug, o.buyer_id,
               u.email AS buyer_email, o.shipping_name, o.shipping_phone, o.status, o.payment_status, o.total,
               coalesce(items.item_count, 0) AS item_count, items.first_item_name,
               ret.status AS return_status, o.cancelled_by, o.cancel_reason, o.created_at, o.updated_at,
        """ + ALERT + " AS alert\n" + BASE_FROM + """
        LEFT JOIN LATERAL (
            SELECT sum(oi.quantity) AS item_count, (array_agg(oi.product_name ORDER BY oi.id))[1] AS first_item_name
            FROM order_item oi
            WHERE oi.order_id = o.id
        ) items ON TRUE
        """;

    private final JdbcClient jdbc;

    public List<AdminOrderResponse> search(AdminOrderFilter filter, Instant now, int page, int size) {
        var where = whereOf(filter, now);

        // Lọc theo cảnh báo: đơn trễ lâu nhất lên đầu để xử lý trước. Còn lại mới đặt trước.
        var orderBy = " ORDER BY o.created_at DESC, o.id";
        if (filter.alert() != null) {
            orderBy = " ORDER BY o.created_at, o.id";
        }

        var sql = SELECT + " WHERE " + where.sql() + orderBy + " LIMIT :limit OFFSET :offset";

        return jdbc.sql(sql)
            .params(where.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(OrderMonitoringQueryRepository::toAdminOrder)
            .list();
    }

    public long count(AdminOrderFilter filter, Instant now) {
        var where = whereOf(filter, now);

        return jdbc.sql("SELECT count(*) " + BASE_FROM + " WHERE " + where.sql())
            .params(where.params())
            .query(Long.class)
            .single();
    }

    /**
     * Đếm theo trạng thái + cảnh báo trên cùng bộ lọc, bỏ qua {@code status}/{@code alert} để làm số cho từng tab.
     */
    public AdminOrderSummaryResponse summary(AdminOrderFilter filter, Instant now) {
        var unfiltered = new AdminOrderFilter(
            null, null, filter.shopId(), filter.buyerId(), filter.query(), filter.from(), filter.to()
        );
        var where = whereOf(unfiltered, now);

        var sql = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE o.status = 'PENDING') AS pending,
                   count(*) FILTER (WHERE o.status = 'CONFIRMED') AS confirmed,
                   count(*) FILTER (WHERE o.status = 'SHIPPING') AS shipping,
                   count(*) FILTER (WHERE o.status = 'DELIVERED') AS delivered,
                   count(*) FILTER (WHERE o.status = 'CANCELLED') AS cancelled,
                   count(*) FILTER (WHERE alert.code = 'CONFIRM_LATE') AS confirm_late,
                   count(*) FILTER (WHERE alert.code = 'HANDOVER_LATE') AS handover_late,
                   count(*) FILTER (WHERE alert.code = 'DELIVERY_LATE') AS delivery_late,
                   count(*) FILTER (WHERE alert.code = 'RETURN_OPEN') AS return_open
            """ + BASE_FROM + " CROSS JOIN LATERAL (SELECT " + ALERT + " AS code) alert WHERE " + where.sql();

        return jdbc.sql(sql)
            .params(where.params())
            .query((rs, rowNum) -> new AdminOrderSummaryResponse(
                rs.getLong("total"),
                rs.getLong("pending"),
                rs.getLong("confirmed"),
                rs.getLong("shipping"),
                rs.getLong("delivered"),
                rs.getLong("cancelled"),
                rs.getLong("confirm_late"),
                rs.getLong("handover_late"),
                rs.getLong("delivery_late"),
                rs.getLong("return_open")
            ))
            .single();
    }

    public Optional<AdminOrderResponse> findById(UUID id, Instant now) {
        return jdbc.sql(SELECT + " WHERE o.id = :id")
            .params(cutoffsOf(now))
            .param("id", id)
            .query(OrderMonitoringQueryRepository::toAdminOrder)
            .optional();
    }

    public String shopStatusOf(UUID orderId) {
        return jdbc.sql("SELECT s.status FROM orders o JOIN shop s ON s.id = o.shop_id WHERE o.id = :id")
            .param("id", orderId)
            .query(String.class)
            .single();
    }

    /**
     * Vai trò người thao tác suy từ dữ liệu: bước hủy lấy theo {@code cancelled_by}, trùng người mua là BUYER,
     * thành viên shop là SELLER, còn lại là ADMIN. Không có người thao tác là SYSTEM.
     */
    public List<OrderTimelineResponse> timeline(UUID orderId) {
        var sql = """
            SELECT h.from_status, h.to_status, h.changed_by, u.email AS changed_by_email, h.note, h.created_at,
                   CASE
                       WHEN h.to_status = 'CANCELLED' AND o.cancelled_by IS NOT NULL THEN o.cancelled_by
                       WHEN h.changed_by IS NULL THEN 'SYSTEM'
                       WHEN h.changed_by = o.buyer_id THEN 'BUYER'
                       WHEN EXISTS (
                           SELECT 1 FROM shop_member m WHERE m.shop_id = o.shop_id AND m.user_id = h.changed_by
                       ) THEN 'SELLER'
                       ELSE 'ADMIN'
                   END AS actor
            FROM order_status_history h
            JOIN orders o ON o.id = h.order_id
            LEFT JOIN user_profile u ON u.id = h.changed_by
            WHERE h.order_id = :order
            ORDER BY h.created_at, h.id
            """;

        return jdbc.sql(sql)
            .param("order", orderId)
            .query((rs, rowNum) -> new OrderTimelineResponse(
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("actor"),
                rs.getObject("changed_by", UUID.class),
                rs.getString("changed_by_email"),
                rs.getString("note"),
                instantOf(rs, "created_at")
            ))
            .list();
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    private static Where whereOf(AdminOrderFilter filter, Instant now) {
        var clauses = new ArrayList<String>();
        var params = cutoffsOf(now);
        clauses.add("TRUE");

        if (filter.status() != null) {
            clauses.add("o.status = :status");
            params.put("status", filter.status().name());
        }

        if (filter.alert() != null) {
            clauses.add("(" + ALERT + ") = :alert");
            params.put("alert", filter.alert().name());
        }

        if (filter.shopId() != null) {
            clauses.add("o.shop_id = :shop");
            params.put("shop", filter.shopId());
        }

        if (filter.buyerId() != null) {
            clauses.add("o.buyer_id = :buyer");
            params.put("buyer", filter.buyerId());
        }

        var query = filter.query();
        if (query != null && !query.isBlank()) {
            clauses.add("""
                (o.code ILIKE :q OR o.shipping_name ILIKE :q OR o.shipping_phone ILIKE :q
                 OR s.name ILIKE :q OR u.email ILIKE :q)""");
            params.put("q", SqlLike.contains(query));
        }

        if (filter.from() != null) {
            clauses.add("o.created_at >= :from");
            params.put("from", utcOf(filter.from().atStartOfDay(ZONE).toInstant()));
        }

        // "đến ngày X" tính trọn ngày X: nhỏ hơn 0 giờ ngày hôm sau.
        if (filter.to() != null) {
            clauses.add("o.created_at < :toExclusive");
            params.put("toExclusive", utcOf(filter.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
        }

        return new Where(String.join(" AND ", clauses), params);
    }

    private static Map<String, Object> cutoffsOf(Instant now) {
        var params = new HashMap<String, Object>();
        params.put("confirmCutoff", utcOf(now.minus(CONFIRM_SLA)));
        params.put("handoverCutoff", utcOf(now.minus(HANDOVER_SLA)));
        params.put("deliveryCutoff", utcOf(now.minus(DELIVERY_SLA)));
        return params;
    }

    private static OffsetDateTime utcOf(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static AdminOrderResponse toAdminOrder(ResultSet rs, int rowNum) throws SQLException {
        return new AdminOrderResponse(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getObject("shop_id", UUID.class),
            rs.getString("shop_name"),
            rs.getString("shop_slug"),
            rs.getObject("buyer_id", UUID.class),
            rs.getString("buyer_email"),
            rs.getString("shipping_name"),
            rs.getString("shipping_phone"),
            rs.getString("status"),
            rs.getString("payment_status"),
            rs.getLong("total"),
            rs.getLong("item_count"),
            rs.getString("first_item_name"),
            rs.getString("alert"),
            rs.getString("return_status"),
            rs.getString("cancelled_by"),
            rs.getString("cancel_reason"),
            instantOf(rs, "created_at"),
            instantOf(rs, "updated_at")
        );
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
