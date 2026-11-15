package com.minhnbnt.fashionstore.features.catalog.search;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** ANN top-N trên diskann. Không lọc bên trong index: lấy dư rồi service dedupe/cắt. */
@Repository
@RequiredArgsConstructor
public class ImageSearchRepository {

    private final JdbcClient jdbc;

    public record Scored(
        UUID productId,
        UUID shopId,
        String name,
        String slug,
        @Nullable Long minPrice,
        @Nullable String defaultImageKey,
        double distance
    ) {
    }

    private static final String SQL = """
        WITH prices AS (
            SELECT product_id, min(price) AS min_price FROM product_variant GROUP BY product_id
        ),
        covers AS (
            SELECT DISTINCT ON (pi.product_id) pi.product_id, u.s3_key
            FROM product_image pi
            JOIN upload u ON u.id = pi.upload_id
            ORDER BY pi.product_id, pi.sort_order
        )
        SELECT
            p.id AS id,
            p.shop_id AS shop_id,
            p.name AS name,
            p.slug AS slug,
            prices.min_price AS min_price,
            covers.s3_key AS default_image,
            pe.embedding <=> cast(:vec as vector) AS distance
        FROM product_embedding pe
        JOIN product p ON p.id = pe.product_id AND p.status = 'ACTIVE'
        JOIN shop s ON s.id = p.shop_id AND s.status = 'APPROVED'
        LEFT JOIN prices ON prices.product_id = p.id
        LEFT JOIN covers ON covers.product_id = p.id
        WHERE pe.embedding IS NOT NULL
        ORDER BY distance, p.id
        LIMIT :fetch
        """;

    public List<Scored> search(float[] vector, int fetch) {
        return jdbc.sql(SQL)
            .param("vec", new PGvector(vector).getValue())
            .param("fetch", fetch)
            .query((rs, rowNum) -> new Scored(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("shop_id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getObject("min_price", Long.class),
                rs.getString("default_image"),
                rs.getDouble("distance")
            ))
            .list();
    }
}
