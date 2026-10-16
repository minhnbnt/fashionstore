package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
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
import java.util.Map;

/**
 * Read model số liệu đơn hàng cho dashboard admin.
 */
@Repository
@RequiredArgsConstructor
public class OrderAdminQueryRepository {

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
}
