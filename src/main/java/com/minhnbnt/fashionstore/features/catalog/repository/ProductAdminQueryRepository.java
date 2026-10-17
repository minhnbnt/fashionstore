package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.admin.ProductModerationHistoryResponse;
import com.minhnbnt.fashionstore.infrastructure.SqlLike;
import com.minhnbnt.fashionstore.model.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model cho màn kiểm duyệt sản phẩm: product + shop + danh mục + tổng hợp biến thể + ảnh đầu.
 */
@Repository
@RequiredArgsConstructor
public class ProductAdminQueryRepository {

    private static final String BASE_FROM = """
        FROM product p
        JOIN shop s ON s.id = p.shop_id
        LEFT JOIN category c ON c.id = p.category_id
        """;

    private static final String SELECT = """
        SELECT p.id, p.shop_id, s.name AS shop_name, s.slug AS shop_slug, s.status AS shop_status,
               p.category_id, c.name AS category_name, p.name, p.slug, p.status, p.violation_type, p.status_reason,
               coalesce(v.min_price, 0) AS min_price, coalesce(v.max_price, 0) AS max_price,
               coalesce(v.total_stock, 0) AS total_stock, coalesce(v.variant_count, 0) AS variant_count,
               img.s3_key AS image_key, p.created_at, p.updated_at
        """ + BASE_FROM + """
        LEFT JOIN LATERAL (
            SELECT min(pv.price) AS min_price, max(pv.price) AS max_price,
                   sum(pv.stock_quantity) AS total_stock, count(*) AS variant_count
            FROM product_variant pv
            WHERE pv.product_id = p.id
        ) v ON TRUE
        LEFT JOIN LATERAL (
            SELECT pi.s3_key
            FROM product_image pi
            WHERE pi.product_id = p.id
            ORDER BY pi.sort_order, pi.id
            LIMIT 1
        ) img ON TRUE
        """;

    private final JdbcClient jdbc;

    public List<AdminProductResponse> search(
        @Nullable ProductStatus status,
        @Nullable UUID shopId,
        @Nullable String query,
        int page,
        int size
    ) {
        var filter = filterOf(status, shopId, query);

        var sql = SELECT + " WHERE " + filter.where()
            + " ORDER BY p.updated_at DESC, p.id"
            + " LIMIT :limit OFFSET :offset";

        return jdbc.sql(sql)
            .params(filter.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(ProductAdminQueryRepository::toAdminProduct)
            .list();
    }

    public long count(@Nullable ProductStatus status, @Nullable UUID shopId, @Nullable String query) {
        var filter = filterOf(status, shopId, query);

        return jdbc.sql("SELECT count(*) " + BASE_FROM + " WHERE " + filter.where())
            .params(filter.params())
            .query(Long.class)
            .single();
    }

    public Optional<AdminProductResponse> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE p.id = :id")
            .param("id", id)
            .query(ProductAdminQueryRepository::toAdminProduct)
            .optional();
    }

    public List<ProductModerationHistoryResponse> history(UUID productId) {
        var sql = """
            SELECT h.from_status, h.to_status, h.violation_type, h.changed_by, u.email AS changed_by_email,
                   h.reason, h.created_at
            FROM product_moderation_history h
            LEFT JOIN user_profile u ON u.id = h.changed_by
            WHERE h.product_id = :product
            ORDER BY h.created_at, h.id
            """;

        return jdbc.sql(sql)
            .param("product", productId)
            .query((rs, rowNum) -> new ProductModerationHistoryResponse(
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("violation_type"),
                rs.getObject("changed_by", UUID.class),
                rs.getString("changed_by_email"),
                rs.getString("reason"),
                instantOf(rs, "created_at")
            ))
            .list();
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private static Filter filterOf(@Nullable ProductStatus status, @Nullable UUID shopId, @Nullable String query) {
        var clauses = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        clauses.add("TRUE");

        if (status != null) {
            clauses.add("p.status = :status");
            params.put("status", status.name());
        }

        if (shopId != null) {
            clauses.add("p.shop_id = :shop");
            params.put("shop", shopId);
        }

        if (query != null && !query.isBlank()) {
            clauses.add("""
                (p.name ILIKE :q OR p.slug ILIKE :q OR s.name ILIKE :q
                 OR EXISTS (SELECT 1 FROM product_variant sv WHERE sv.product_id = p.id AND sv.sku ILIKE :q))""");
            params.put("q", SqlLike.contains(query));
        }

        return new Filter(String.join(" AND ", clauses), params);
    }

    private static AdminProductResponse toAdminProduct(ResultSet rs, int rowNum) throws SQLException {
        return new AdminProductResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("shop_id", UUID.class),
            rs.getString("shop_name"),
            rs.getString("shop_slug"),
            rs.getString("shop_status"),
            rs.getObject("category_id", UUID.class),
            rs.getString("category_name"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getString("violation_type"),
            rs.getString("status_reason"),
            rs.getLong("min_price"),
            rs.getLong("max_price"),
            rs.getLong("total_stock"),
            rs.getLong("variant_count"),
            rs.getString("image_key"),
            instantOf(rs, "created_at"),
            instantOf(rs, "updated_at")
        );
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
