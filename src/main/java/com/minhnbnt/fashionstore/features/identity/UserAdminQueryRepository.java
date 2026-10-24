package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.infrastructure.SqlLike;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserRole;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read model cho màn admin quản lý tài khoản.
 */
@Repository
@RequiredArgsConstructor
public class UserAdminQueryRepository {

    private static final String SELECT = """
        SELECT u.id, u.email, u.display_name, u.avatar_key, u.role, u.status, u.status_reason, u.created_at
        FROM user_profile u
        """;

    private final JdbcClient jdbc;

    public List<AdminUserResponse> search(
        @Nullable AccountStatus status,
        @Nullable UserRole role,
        @Nullable String query,
        int page,
        int size
    ) {
        var filter = filterOf(status, role, query);

        var sql = SELECT + " WHERE " + filter.where()
            + " ORDER BY u.created_at DESC, u.id"
            + " LIMIT :limit OFFSET :offset";

        return jdbc.sql(sql)
            .params(filter.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(UserAdminQueryRepository::toAdminUser)
            .list();
    }

    public long count(@Nullable AccountStatus status, @Nullable UserRole role, @Nullable String query) {
        var filter = filterOf(status, role, query);

        return jdbc.sql("SELECT count(*) FROM user_profile u WHERE " + filter.where())
            .params(filter.params())
            .query(Long.class)
            .single();
    }

    public List<StatusHistoryResponse> history(UUID userId) {
        var sql = """
            SELECT h.from_status, h.to_status, h.changed_by, a.email AS changed_by_email, h.reason, h.created_at
            FROM user_status_history h
            LEFT JOIN user_profile a ON a.id = h.changed_by
            WHERE h.user_id = :user
            ORDER BY h.created_at, h.id
            """;

        return jdbc.sql(sql)
            .param("user", userId)
            .query((rs, rowNum) -> new StatusHistoryResponse(
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getObject("changed_by", UUID.class),
                rs.getString("changed_by_email"),
                rs.getString("reason"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
            ))
            .list();
    }

    public Map<AccountStatus, Long> countByStatus() {
        var counts = new EnumMap<AccountStatus, Long>(AccountStatus.class);
        for (var status : AccountStatus.values()) {
            counts.put(status, 0L);
        }

        var rows = jdbc.sql("SELECT status, count(*) AS total FROM user_profile GROUP BY status")
            .query((rs, rowNum) -> Map.entry(AccountStatus.valueOf(rs.getString("status")), rs.getLong("total")))
            .list();
        for (var row : rows) {
            counts.put(row.getKey(), row.getValue());
        }

        return counts;
    }

    public Map<UserRole, Long> countByRole() {
        var counts = new EnumMap<UserRole, Long>(UserRole.class);
        for (var role : UserRole.values()) {
            counts.put(role, 0L);
        }

        var rows = jdbc.sql("SELECT role, count(*) AS total FROM user_profile GROUP BY role")
            .query((rs, rowNum) -> Map.entry(UserRole.valueOf(rs.getString("role")), rs.getLong("total")))
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
            FROM user_profile
            WHERE created_at >= :from
            GROUP BY day
            """;

        return jdbc.sql(sql)
            .param("zone", zone.getId())
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .query((rs, rowNum) -> Map.entry(rs.getObject("day", LocalDate.class), rs.getLong("total")))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private static Filter filterOf(@Nullable AccountStatus status, @Nullable UserRole role, @Nullable String query) {
        var clauses = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        clauses.add("TRUE");

        if (status != null) {
            clauses.add("u.status = :status");
            params.put("status", status.name());
        }

        if (role != null) {
            clauses.add("u.role = :role");
            params.put("role", role.name());
        }

        if (query != null && !query.isBlank()) {
            clauses.add("(u.email ILIKE :q OR u.display_name ILIKE :q)");
            params.put("q", SqlLike.contains(query));
        }

        return new Filter(String.join(" AND ", clauses), params);
    }

    private static AdminUserResponse toAdminUser(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUserResponse(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("avatar_key"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getString("status_reason"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
