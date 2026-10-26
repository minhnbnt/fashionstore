package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopActivityResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model nhật ký hoạt động: join user_profile để lấy email/tên actor.
 */
@Repository
@RequiredArgsConstructor
public class ShopActivityQueryRepository {

    private final JdbcClient jdbc;

    public PageResponse<ShopActivityResponse> page(
        UUID shopId,
        @Nullable String action,
        int page,
        int size
    ) {
        var pageSize = Math.clamp(size, 1, 100);
        var pageIndex = Math.max(page, 0);
        var offset = (long) pageIndex * pageSize;

        var filter = "";
        if (action != null && !action.isBlank()) {
            filter = " AND a.action = :action\n";
        }

        var sql = """
            SELECT a.id, a.member_id, u.email, u.display_name, a.action, a.target_type,
                   a.target_id, a.detail, a.created_at
            FROM shop_member_audit a
            LEFT JOIN user_profile u ON u.id = a.member_id
            WHERE a.shop_id = :shop
            """ + filter + """
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT :limit OFFSET :offset
            """;

        var query = jdbc.sql(sql)
            .param("shop", shopId)
            .param("limit", pageSize)
            .param("offset", offset);
        if (action != null && !action.isBlank()) {
            query = query.param("action", action);
        }

        var items = query.query(ShopActivityQueryRepository::toResponse).list();
        var total = count(shopId, action);

        return new PageResponse<>(items, total, pageIndex, pageSize);
    }

    private long count(UUID shopId, @Nullable String action) {
        var filter = "";
        if (action != null && !action.isBlank()) {
            filter = " AND action = :action";
        }

        var query = jdbc.sql("SELECT count(*) FROM shop_member_audit WHERE shop_id = :shop" + filter)
            .param("shop", shopId);
        if (action != null && !action.isBlank()) {
            query = query.param("action", action);
        }

        return query.query(Long.class).single();
    }

    private static ShopActivityResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        var email = rs.getString("email");
        var displayName = rs.getString("display_name");
        if (email == null) {
            email = "";
        }
        if (displayName == null) {
            displayName = "";
        }

        return new ShopActivityResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("member_id", UUID.class),
            email,
            displayName,
            rs.getString("action"),
            rs.getString("target_type"),
            rs.getObject("target_id", UUID.class),
            rs.getString("detail"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
