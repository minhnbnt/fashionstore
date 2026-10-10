package com.minhnbnt.fashionstore.features.catalog.repository;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private final JdbcClient jdbc;

    public record Hit(
        UUID id,
        UUID shopId,
        String name,
        String slug,
        long minPrice,
        @Nullable String defaultImageKey,
        double rank
    ) {
    }

    public List<Hit> search(String query, int limit) {
        var sql = """
            WITH candidates AS (
                SELECT
                    p.id,
                    p.shop_id,
                    p.name,
                    p.slug,
                    coalesce(p.name, '') || ' ' || coalesce(p.description, '') AS doc
                FROM product p
                JOIN shop s ON s.id = p.shop_id
                    AND s.status = 'APPROVED'
                WHERE p.status = 'ACTIVE'
            ),
            prices AS (
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
                c.id AS id,
                c.shop_id AS shop_id,
                c.name AS name,
                c.slug AS slug,
                COALESCE(prices.min_price, 0) AS min_price,
                covers.s3_key AS default_image,
                (c.doc <@> to_bm25query(:q, 'idx_product_bm25')) AS rank
            FROM candidates c
            LEFT JOIN prices ON prices.product_id = c.id
            LEFT JOIN covers ON covers.product_id = c.id
            ORDER BY rank
            LIMIT :limit
            """;

        return jdbc.sql(sql)
            .param("q", query)
            .param("limit", limit)
            .query((rs, rowNum) -> new Hit(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("shop_id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getLong("min_price"),
                rs.getString("default_image"),
                rs.getDouble("rank")
            ))
            .list();
    }
}
