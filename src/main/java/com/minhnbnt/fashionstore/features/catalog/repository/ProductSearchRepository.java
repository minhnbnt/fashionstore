package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.dto.catalog.ProductSort;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private final JdbcClient jdbc;

    // Phải giữ y hệt expression lúc tạo idx_product_bm25 (V2), lệch là planner bỏ qua index.
    private static final String DOCUMENT = "(coalesce(p.name, '') || ' ' || coalesce(p.description, ''))";

    private static final String RANK = DOCUMENT + " <@> to_bm25query(:q, 'idx_product_bm25')";

    // Size chữ xếp theo thứ tự mặc thường gặp, size lạ (Freesize, 38, 40...) xếp sau theo A-Z.
    private static final String SIZE_ORDER =
        "array_position(ARRAY['XS','S','M','L','XL','XXL','XXXL']::text[], v.size)";

    private static final String BASE_FROM = """
        FROM product p
        JOIN shop s ON s.id = p.shop_id
            AND s.status = 'APPROVED'
        LEFT JOIN category c ON c.id = p.category_id
        """;

    public record Hit(
        UUID id,
        UUID shopId,
        String name,
        String slug,
        long minPrice,
        @Nullable String defaultImageKey,
        @Nullable Double rank
    ) {
    }

    /**
     * Bộ lọc đã chuẩn hóa ở service: chuỗi rỗng đã thành null, sort đã chốt, limit đã kẹp.
     */
    public record Criteria(
        @Nullable String query,
        @Nullable String categorySlug,
        @Nullable Long minPrice,
        @Nullable Long maxPrice,
        @Nullable String size,
        @Nullable String color,
        ProductSort sort,
        long offset,
        int limit
    ) {
    }

    public record PriceRange(@Nullable Long min, @Nullable Long max) {
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    public List<Hit> search(Criteria criteria) {
        var filter = filterOf(criteria, true);

        var sql = """
            WITH prices AS (
                SELECT
                    product_id,
                    min(price) AS min_price
                FROM product_variant
                GROUP BY product_id
            ),
            covers AS (
                SELECT DISTINCT ON (product_id)
                    product_id,
                    s3_key
                FROM product_image
                ORDER BY product_id, sort_order
            )
            SELECT
                p.id AS id,
                p.shop_id AS shop_id,
                p.name AS name,
                p.slug AS slug,
                coalesce(prices.min_price, 0) AS min_price,
                covers.s3_key AS default_image,
                %s AS rank
            %s
            LEFT JOIN prices ON prices.product_id = p.id
            LEFT JOIN covers ON covers.product_id = p.id
            WHERE %s
            ORDER BY %s
            LIMIT :limit OFFSET :offset
            """.formatted(rankColumn(criteria), BASE_FROM, filter.where(), orderBy(criteria.sort()));

        return jdbc.sql(sql)
            .params(filter.params())
            .param("limit", criteria.limit())
            .param("offset", criteria.offset())
            .query((rs, rowNum) -> new Hit(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("shop_id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getLong("min_price"),
                rs.getString("default_image"),
                rs.getObject("rank", Double.class)
            ))
            .list();
    }

    public long count(Criteria criteria) {
        var filter = filterOf(criteria, true);

        var sql = "SELECT count(*) " + BASE_FROM + " WHERE " + filter.where();

        return jdbc.sql(sql)
            .params(filter.params())
            .query(Long.class)
            .single();
    }

    /**
     * Facet tính trên tập khớp từ khóa + danh mục, bỏ qua size/màu/giá đang chọn,
     * để người dùng vẫn thấy lựa chọn khác mà đổi.
     */
    public List<String> sizes(Criteria criteria) {
        var filter = filterOf(criteria, false);

        var sql = """
            SELECT v.size
            %s
            JOIN product_variant v ON v.product_id = p.id
            WHERE %s
            GROUP BY v.size
            ORDER BY %s NULLS LAST, v.size
            """.formatted(BASE_FROM, filter.where(), SIZE_ORDER);

        return jdbc.sql(sql)
            .params(filter.params())
            .query(String.class)
            .list();
    }

    public List<String> colors(Criteria criteria) {
        var filter = filterOf(criteria, false);

        var sql = """
            SELECT DISTINCT v.color
            %s
            JOIN product_variant v ON v.product_id = p.id
            WHERE %s
            ORDER BY v.color
            """.formatted(BASE_FROM, filter.where());

        return jdbc.sql(sql)
            .params(filter.params())
            .query(String.class)
            .list();
    }

    public PriceRange priceRange(Criteria criteria) {
        var filter = filterOf(criteria, false);

        var sql = """
            SELECT
                min(v.price) AS min_price,
                max(v.price) AS max_price
            %s
            JOIN product_variant v ON v.product_id = p.id
            WHERE %s
            """.formatted(BASE_FROM, filter.where());

        return jdbc.sql(sql)
            .params(filter.params())
            .query((rs, rowNum) -> new PriceRange(
                rs.getObject("min_price", Long.class),
                rs.getObject("max_price", Long.class)
            ))
            .single();
    }

    private static Filter filterOf(Criteria criteria, boolean includeVariantFilters) {
        var conditions = new ArrayList<String>();
        var params = new HashMap<String, Object>();

        conditions.add("p.status = 'ACTIVE'");

        if (criteria.query() != null) {
            // Doc không chứa token nào của query có điểm 0, chỉ giữ doc thực sự khớp.
            conditions.add(RANK + " < 0");
            params.put("q", criteria.query());
        }

        if (criteria.categorySlug() != null) {
            conditions.add("c.slug = :category");
            params.put("category", criteria.categorySlug());
        }

        if (includeVariantFilters) {
            var variantCondition = variantCondition(criteria, params);
            if (variantCondition != null) {
                conditions.add(variantCondition);
            }
        }

        return new Filter(String.join("\n    AND ", conditions), params);
    }

    /**
     * Size, màu, khoảng giá phải khớp trên CÙNG một variant: lọc "L + Trắng" không được trả về
     * áo chỉ có "L Đen" và "S Trắng".
     */
    private static @Nullable String variantCondition(Criteria criteria, Map<String, Object> params) {
        var conditions = new ArrayList<String>();

        if (criteria.size() != null) {
            conditions.add("lower(v.size) = lower(:size)");
            params.put("size", criteria.size());
        }
        if (criteria.color() != null) {
            conditions.add("lower(v.color) = lower(:color)");
            params.put("color", criteria.color());
        }
        if (criteria.minPrice() != null) {
            conditions.add("v.price >= :minPrice");
            params.put("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            conditions.add("v.price <= :maxPrice");
            params.put("maxPrice", criteria.maxPrice());
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return "EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = p.id AND "
            + String.join(" AND ", conditions)
            + ")";
    }

    private static String rankColumn(Criteria criteria) {
        if (criteria.query() == null) {
            return "CAST(NULL AS double precision)";
        }

        return "(" + RANK + ")";
    }

    private static String orderBy(ProductSort sort) {
        // p.id cuối cùng để thứ tự ổn định giữa các trang khi giá/thời gian trùng nhau.
        return switch (sort) {
            case RELEVANCE -> "rank, p.created_at DESC, p.id";
            case NEWEST -> "p.created_at DESC, p.id";
            case PRICE_ASC -> "coalesce(prices.min_price, 0), p.id";
            case PRICE_DESC -> "coalesce(prices.min_price, 0) DESC, p.id";
        };
    }
}
