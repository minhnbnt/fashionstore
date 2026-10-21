package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.dto.admin.AdminProductReportResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordResponse;
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
 * Read model cho màn kiểm duyệt sản phẩm: product + shop + danh mục + tổng hợp biến thể + ảnh đầu
 * + tổng hợp báo cáo đang mở.
 */
@Repository
@RequiredArgsConstructor
public class ProductAdminQueryRepository {

    private static final String BASE_FROM = """
        FROM product p
        JOIN shop s ON s.id = p.shop_id
        LEFT JOIN category c ON c.id = p.category_id
        """;

    private static final String HAS_OPEN_REPORT = """
        EXISTS (SELECT 1 FROM product_report r WHERE r.product_id = p.id AND r.status = 'OPEN')""";

    private static final String SELECT = """
        SELECT p.id, p.shop_id, s.name AS shop_name, s.slug AS shop_slug, s.status AS shop_status,
               p.category_id, c.name AS category_name, p.name, p.slug, p.status, p.violation_type, p.status_reason,
               coalesce(v.min_price, 0) AS min_price, coalesce(v.max_price, 0) AS max_price,
               coalesce(v.total_stock, 0) AS total_stock, coalesce(v.variant_count, 0) AS variant_count,
               img.s3_key AS image_key, p.created_at, p.updated_at,
               rep.open_reports, rep.top_reason, rep.last_reported_at,
               coalesce(rep.system_flagged, FALSE) AS system_flagged
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
        LEFT JOIN LATERAL (
            SELECT count(*) AS open_reports,
                   mode() WITHIN GROUP (ORDER BY r.reason) AS top_reason,
                   max(r.created_at) AS last_reported_at,
                   bool_or(r.source = 'SYSTEM') AS system_flagged
            FROM product_report r
            WHERE r.product_id = p.id AND r.status = 'OPEN'
        ) rep ON TRUE
        """;

    private static final int REPORT_LIMIT = 100;

    private final JdbcClient jdbc;

    public List<AdminProductResponse> search(
        @Nullable ProductStatus status,
        @Nullable UUID shopId,
        @Nullable String query,
        boolean reported,
        int page,
        int size
    ) {
        var filter = filterOf(status, shopId, query, reported);

        var sql = SELECT + " WHERE " + filter.where()
            + " ORDER BY " + orderOf(reported)
            + " LIMIT :limit OFFSET :offset";

        return jdbc.sql(sql)
            .params(filter.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(ProductAdminQueryRepository::toAdminProduct)
            .list();
    }

    public long count(@Nullable ProductStatus status, @Nullable UUID shopId, @Nullable String query, boolean reported) {
        var filter = filterOf(status, shopId, query, reported);

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

    public List<AdminProductReportResponse> reports(UUID productId) {
        var sql = """
            SELECT r.id, r.source, r.reason, r.description, reporter.email AS reporter_email, r.status,
                   r.created_at, resolver.email AS resolved_by_email, r.resolved_at, r.resolution_note
            FROM product_report r
            LEFT JOIN user_profile reporter ON reporter.id = r.reporter_id
            LEFT JOIN user_profile resolver ON resolver.id = r.resolved_by
            WHERE r.product_id = :product
            ORDER BY r.created_at DESC, r.id
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("product", productId)
            .param("limit", REPORT_LIMIT)
            .query((rs, rowNum) -> new AdminProductReportResponse(
                rs.getObject("id", UUID.class),
                rs.getString("source"),
                rs.getString("reason"),
                rs.getString("description"),
                rs.getString("reporter_email"),
                rs.getString("status"),
                instantOf(rs, "created_at"),
                rs.getString("resolved_by_email"),
                nullableInstantOf(rs, "resolved_at"),
                rs.getString("resolution_note")
            ))
            .list();
    }

    public List<BannedKeywordResponse> keywords() {
        var sql = """
            SELECT k.id, k.keyword, k.violation_type, u.email AS created_by_email, k.created_at
            FROM banned_keyword k
            LEFT JOIN user_profile u ON u.id = k.created_by
            ORDER BY k.created_at DESC, k.id
            """;

        return jdbc.sql(sql)
            .query(ProductAdminQueryRepository::toKeyword)
            .list();
    }

    public Optional<BannedKeywordResponse> findKeyword(UUID id) {
        var sql = """
            SELECT k.id, k.keyword, k.violation_type, u.email AS created_by_email, k.created_at
            FROM banned_keyword k
            LEFT JOIN user_profile u ON u.id = k.created_by
            WHERE k.id = :id
            """;

        return jdbc.sql(sql)
            .param("id", id)
            .query(ProductAdminQueryRepository::toKeyword)
            .optional();
    }

    /**
     * Ứng viên khi thêm từ khóa mới: hàng đang bán/đang ẩn có chứa chuỗi. ILIKE chỉ lọc thô,
     * so khớp nguyên từ làm ở Java (KeywordScanner).
     */
    public List<ScanCandidate> scanCandidates(String keyword) {
        var sql = """
            SELECT p.id, p.name, p.description
            FROM product p
            WHERE p.status IN ('ACTIVE', 'HIDDEN') AND (p.name ILIKE :q OR p.description ILIKE :q)
            """;

        return jdbc.sql(sql)
            .param("q", SqlLike.contains(keyword))
            .query((rs, rowNum) -> new ScanCandidate(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description")
            ))
            .list();
    }

    public record ScanCandidate(UUID id, String name, String description) {
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private static Filter filterOf(
        @Nullable ProductStatus status,
        @Nullable UUID shopId,
        @Nullable String query,
        boolean reported
    ) {
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

        if (reported) {
            clauses.add(HAS_OPEN_REPORT);
        }

        return new Filter(String.join(" AND ", clauses), params);
    }

    /**
     * Hàng đợi báo cáo: nhiều báo cáo nhất lên đầu, bằng nhau thì bị báo gần nhất trước.
     */
    private static String orderOf(boolean reported) {
        if (reported) {
            return "rep.open_reports DESC, rep.last_reported_at DESC, p.id";
        }
        return "p.updated_at DESC, p.id";
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
            instantOf(rs, "updated_at"),
            rs.getLong("open_reports"),
            rs.getString("top_reason"),
            nullableInstantOf(rs, "last_reported_at"),
            rs.getBoolean("system_flagged")
        );
    }

    private static BannedKeywordResponse toKeyword(ResultSet rs, int rowNum) throws SQLException {
        return new BannedKeywordResponse(
            rs.getObject("id", UUID.class),
            rs.getString("keyword"),
            rs.getString("violation_type"),
            rs.getString("created_by_email"),
            instantOf(rs, "created_at")
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
