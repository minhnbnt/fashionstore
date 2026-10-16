package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.infrastructure.SqlLike;
import com.minhnbnt.fashionstore.model.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model cho màn admin: shop + owner (join shop_member OWNER → user_profile).
 */
@Repository
@RequiredArgsConstructor
public class ShopAdminQueryRepository {

    private static final String BASE_FROM = """
        FROM shop s
        LEFT JOIN shop_member m ON m.shop_id = s.id AND m.role = 'OWNER'
        LEFT JOIN user_profile u ON u.id = m.user_id
        """;

    private static final String SELECT = """
        SELECT s.id, s.name, s.slug, s.description, s.logo_key, s.banner_key, s.status, s.status_reason,
               u.id AS owner_id, u.email AS owner_email, u.display_name AS owner_name, u.status AS owner_status,
               s.submitted_at, s.approved_at, s.created_at, s.updated_at
        """ + BASE_FROM;

    private final JdbcClient jdbc;

    public List<AdminShopResponse> search(@Nullable ShopStatus status, @Nullable String query, int page, int size) {
        var filter = filterOf(status, query);

        var sql = SELECT + " WHERE " + filter.where()
            + " ORDER BY " + orderOf(status)
            + " LIMIT :limit OFFSET :offset";

        return jdbc.sql(sql)
            .params(filter.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(ShopAdminQueryRepository::toAdminShop)
            .list();
    }

    public long count(@Nullable ShopStatus status, @Nullable String query) {
        var filter = filterOf(status, query);

        return jdbc.sql("SELECT count(*) " + BASE_FROM + " WHERE " + filter.where())
            .params(filter.params())
            .query(Long.class)
            .single();
    }

    public Optional<AdminShopResponse> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE s.id = :id")
            .param("id", id)
            .query(ShopAdminQueryRepository::toAdminShop)
            .optional();
    }

    public List<StatusHistoryResponse> history(UUID shopId) {
        var sql = """
            SELECT h.from_status, h.to_status, h.changed_by, u.email AS changed_by_email, h.reason, h.created_at
            FROM shop_status_history h
            LEFT JOIN user_profile u ON u.id = h.changed_by
            WHERE h.shop_id = :shop
            ORDER BY h.created_at, h.id
            """;

        return jdbc.sql(sql)
            .param("shop", shopId)
            .query((rs, rowNum) -> new StatusHistoryResponse(
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getObject("changed_by", UUID.class),
                rs.getString("changed_by_email"),
                rs.getString("reason"),
                instantOf(rs, "created_at")
            ))
            .list();
    }

    public Map<ShopStatus, Long> countByStatus() {
        var counts = new EnumMap<ShopStatus, Long>(ShopStatus.class);
        for (var status : ShopStatus.values()) {
            counts.put(status, 0L);
        }

        var rows = jdbc.sql("SELECT status, count(*) AS total FROM shop GROUP BY status")
            .query((rs, rowNum) -> Map.entry(ShopStatus.valueOf(rs.getString("status")), rs.getLong("total")))
            .list();
        for (var row : rows) {
            counts.put(row.getKey(), row.getValue());
        }

        return counts;
    }

    /**
     * Đếm bản ghi mới theo ngày tạo (tính theo {@code zone}) từ {@code from} đến nay. Ngày không có thì không có key.
     */
    public Map<LocalDate, Long> countCreatedPerDay(Instant from, ZoneId zone) {
        var sql = """
            SELECT (created_at AT TIME ZONE :zone)::date AS day, count(*) AS total
            FROM shop
            WHERE created_at >= :from
            GROUP BY day
            """;

        var rows = jdbc.sql(sql)
            .param("zone", zone.getId())
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .query((rs, rowNum) -> Map.entry(rs.getObject("day", LocalDate.class), rs.getLong("total")))
            .list();

        var result = new HashMap<LocalDate, Long>();
        for (var row : rows) {
            result.put(row.getKey(), row.getValue());
        }

        return result;
    }

    public List<UUID> findApprovedShopIdsOwnedBy(UUID userId) {
        var sql = """
            SELECT s.id
            FROM shop s
            JOIN shop_member m ON m.shop_id = s.id
            WHERE m.user_id = :user AND m.role = 'OWNER' AND s.status = 'APPROVED'
            ORDER BY s.created_at
            """;

        return jdbc.sql(sql)
            .param("user", userId)
            .query((rs, rowNum) -> rs.getObject("id", UUID.class))
            .list();
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private static Filter filterOf(@Nullable ShopStatus status, @Nullable String query) {
        var clauses = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        clauses.add("TRUE");

        if (status != null) {
            clauses.add("s.status = :status");
            params.put("status", status.name());
        }

        if (query != null && !query.isBlank()) {
            clauses.add("(s.name ILIKE :q OR s.slug ILIKE :q OR u.email ILIKE :q)");
            params.put("q", SqlLike.contains(query));
        }

        return new Filter(String.join(" AND ", clauses), params);
    }

    /**
     * Hàng đợi chờ duyệt: ai nộp trước duyệt trước. Các tab khác: mới thay đổi lên đầu.
     */
    private static String orderOf(@Nullable ShopStatus status) {
        if (status == ShopStatus.PENDING) {
            return "s.submitted_at ASC, s.id";
        }
        return "s.updated_at DESC, s.id";
    }

    private static AdminShopResponse toAdminShop(ResultSet rs, int rowNum) throws SQLException {
        return new AdminShopResponse(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("description"),
            rs.getString("logo_key"),
            rs.getString("banner_key"),
            rs.getString("status"),
            rs.getString("status_reason"),
            rs.getObject("owner_id", UUID.class),
            rs.getString("owner_email"),
            rs.getString("owner_name"),
            rs.getString("owner_status"),
            instantOf(rs, "submitted_at"),
            nullableInstantOf(rs, "approved_at"),
            instantOf(rs, "created_at"),
            instantOf(rs, "updated_at")
        );
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static @Nullable Instant nullableInstantOf(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        if (value == null) {
            return null;
        }
        return value.toInstant();
    }
}
