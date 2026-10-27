package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.ShopCategorySummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopProductCollectionType;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ShopCatalogQueryRepository {

    private final JdbcClient jdbc;

    public List<UUID> findShopProductIds(@NonNull UUID shopId, @NonNull ShopProductQueryRequest request) {
        var sql = new StringBuilder("""
            SELECT p.id,
                   (SELECT COALESCE(SUM(oi.quantity), 0)
                    FROM order_item oi
                    JOIN orders o ON oi.order_id = o.id
                    WHERE oi.product_id = p.id AND o.status != 'CANCELLED'
                   ) AS total_sold,
                   (SELECT COALESCE(MIN(pv.price), 0)
                    FROM product_variant pv
                    WHERE pv.product_id = p.id
                   ) AS min_price,
                   (SELECT COALESCE(MAX(
                       CASE WHEN pv.compare_at_price IS NOT NULL AND pv.compare_at_price > pv.price
                            THEN (pv.compare_at_price - pv.price)::float / pv.compare_at_price
                            ELSE 0
                       END), 0)
                    FROM product_variant pv
                    WHERE pv.product_id = p.id
                   ) AS discount_rate
            FROM product p
            WHERE p.shop_id = :shopId
              AND p.status = 'ACTIVE'
            """);

        if (request.q() != null && !request.q().isBlank()) {
            sql.append(" AND (p.name ILIKE :qLike OR p.description ILIKE :qLike)");
        }

        if (request.categoryId() != null) {
            sql.append(" AND p.category_id = :categoryId");
        }

        if (request.collection() != null) {
            if (request.collection() == ShopProductCollectionType.SALE_OFF) {
                sql.append("""
                     AND EXISTS (
                        SELECT 1 FROM product_variant pv
                        WHERE pv.product_id = p.id
                          AND pv.compare_at_price IS NOT NULL
                          AND pv.compare_at_price > pv.price
                    )
                    """);
            }
        }

        // Sắp xếp
        if (request.collection() == ShopProductCollectionType.BEST_SELLER || "BEST_SELLER".equalsIgnoreCase(request.sort())) {
            sql.append(" ORDER BY total_sold DESC, p.created_at DESC");
        } else if (request.collection() == ShopProductCollectionType.SALE_OFF) {
            sql.append(" ORDER BY discount_rate DESC, p.created_at DESC");
        } else if ("PRICE_ASC".equalsIgnoreCase(request.sort())) {
            sql.append(" ORDER BY min_price ASC, p.created_at DESC");
        } else if ("PRICE_DESC".equalsIgnoreCase(request.sort())) {
            sql.append(" ORDER BY min_price DESC, p.created_at DESC");
        } else {
            // NEW_ARRIVALS hoặc NEWEST
            sql.append(" ORDER BY p.created_at DESC");
        }

        int limit = request.limit();
        if (limit <= 0 || limit > 50) {
            limit = 12;
        }
        int page = request.page();
        if (page < 0) {
            page = 0;
        }
        int offset = page * limit;

        sql.append(" LIMIT :limit OFFSET :offset");

        var query = jdbc.sql(sql.toString())
            .param("shopId", shopId)
            .param("limit", limit)
            .param("offset", offset);

        if (request.q() != null && !request.q().isBlank()) {
            query.param("qLike", "%" + request.q().trim() + "%");
        }
        if (request.categoryId() != null) {
            query.param("categoryId", request.categoryId());
        }

        return query.query((rs, rowNum) -> rs.getObject("id", UUID.class)).list();
    }

    public long countShopProducts(@NonNull UUID shopId, @NonNull ShopProductQueryRequest request) {
        var sql = new StringBuilder("""
            SELECT COUNT(p.id)
            FROM product p
            WHERE p.shop_id = :shopId
              AND p.status = 'ACTIVE'
            """);

        if (request.q() != null && !request.q().isBlank()) {
            sql.append(" AND (p.name ILIKE :qLike OR p.description ILIKE :qLike)");
        }

        if (request.categoryId() != null) {
            sql.append(" AND p.category_id = :categoryId");
        }

        if (request.collection() != null) {
            if (request.collection() == ShopProductCollectionType.SALE_OFF) {
                sql.append("""
                     AND EXISTS (
                        SELECT 1 FROM product_variant pv
                        WHERE pv.product_id = p.id
                          AND pv.compare_at_price IS NOT NULL
                          AND pv.compare_at_price > pv.price
                    )
                    """);
            }
        }

        var query = jdbc.sql(sql.toString()).param("shopId", shopId);

        if (request.q() != null && !request.q().isBlank()) {
            query.param("qLike", "%" + request.q().trim() + "%");
        }
        if (request.categoryId() != null) {
            query.param("categoryId", request.categoryId());
        }

        var count = query.query(Long.class).single();
        if (count == null) {
            return 0L;
        }
        return count;
    }

    public List<ShopCategorySummaryResponse> findShopCategories(@NonNull UUID shopId) {
        var sql = """
            SELECT c.id, c.name, c.slug, COUNT(p.id) AS product_count
            FROM category c
            JOIN product p ON p.category_id = c.id
            WHERE p.shop_id = :shopId
              AND p.status = 'ACTIVE'
            GROUP BY c.id, c.name, c.slug
            HAVING COUNT(p.id) > 0
            ORDER BY product_count DESC, c.name ASC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new ShopCategorySummaryResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getLong("product_count")
            ))
            .list();
    }
}
