package com.minhnbnt.fashionstore.features.review.repository;

import com.minhnbnt.fashionstore.dto.review.SellerReviewProductOptionResponse;
import com.minhnbnt.fashionstore.model.Review;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SellerReviewQueryRepository {

    private final JdbcClient jdbc;

    public record SellerReviewRow(Review review, String productName) {
    }

    public record ReviewTrendRow(String label, long reviewCount, double averageRating) {
    }

    public List<SellerReviewRow> findShopReviews(
        UUID shopId,
        boolean onlyUnreplied,
        @Nullable Integer stars,
        @Nullable UUID productId
    ) {
        var sql = new StringBuilder("""
            SELECT r.id,
                   r.product_id,
                   r.buyer_id,
                   r.order_id,
                   r.buyer_display_name,
                   r.variant_label,
                   r.stars,
                   r.comment,
                   r.image_keys,
                   r.video_key,
                   r.tags,
                   r.is_anonymous,
                   r.helpful_count,
                   r.seller_reply,
                   r.replied_at,
                   r.is_reported,
                   r.report_reason,
                   r.edited_at,
                   r.created_at,
                   r.updated_at,
                   p.name AS product_name
            FROM review r
            JOIN product p ON p.id = r.product_id
            WHERE p.shop_id = :shopId
            """);

        var params = new LinkedHashMap<String, Object>();
        params.put("shopId", shopId);

        if (onlyUnreplied) {
            sql.append(" AND r.seller_reply IS NULL");
        }
        if (stars != null) {
            sql.append(" AND r.stars = :stars");
            params.put("stars", stars);
        }
        if (productId != null) {
            sql.append(" AND r.product_id = :productId");
            params.put("productId", productId);
        }

        sql.append(" ORDER BY r.created_at DESC, r.id DESC");

        var spec = jdbc.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query((rs, rowNum) -> new SellerReviewRow(
            mapReview(rs),
            Objects.requireNonNull(rs.getString("product_name"))
        )).list();
    }

    public List<SellerReviewProductOptionResponse> findShopReviewProductOptions(UUID shopId) {
        var sql = """
            SELECT p.id,
                   p.name,
                   COUNT(r.id) AS review_count
            FROM product p
            LEFT JOIN review r ON r.product_id = p.id
            WHERE p.shop_id = :shopId
              AND (p.status <> 'DELETED' OR r.id IS NOT NULL)
            GROUP BY p.id, p.name
            ORDER BY review_count DESC, p.name ASC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new SellerReviewProductOptionResponse(
                Objects.requireNonNull(rs.getObject("id", UUID.class)),
                Objects.requireNonNull(rs.getString("name")),
                rs.getLong("review_count")
            ))
            .list();
    }

    public List<ReviewTrendRow> findShopReviewTrend(
        UUID shopId,
        String period,
        Instant from,
        Instant to
    ) {
        String formatExpr;
        if ("week".equalsIgnoreCase(period)) {
            formatExpr = "to_char(date_trunc('week', r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'YYYY-\"W\"IW')";
        } else if ("month".equalsIgnoreCase(period)) {
            formatExpr = "to_char(r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM')";
        } else {
            formatExpr = "to_char(r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD')";
        }

        var sql = """
            SELECT
                """ + formatExpr + """
                AS period_label,
                COUNT(*) AS review_count,
                AVG(r.stars)::double precision AS average_rating
            FROM review r
            JOIN product p ON p.id = r.product_id
            WHERE p.shop_id = :shopId
              AND r.created_at >= :from
              AND r.created_at < :to
            GROUP BY period_label
            ORDER BY period_label ASC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .param("from", Timestamp.from(from))
            .param("to", Timestamp.from(to))
            .query((rs, rowNum) -> new ReviewTrendRow(
                Objects.requireNonNull(rs.getString("period_label")),
                rs.getLong("review_count"),
                rs.getDouble("average_rating")
            ))
            .list();
    }

    private static Review mapReview(ResultSet rs) throws SQLException {
        return new Review(
            Objects.requireNonNull(rs.getObject("id", UUID.class)),
            Objects.requireNonNull(rs.getObject("product_id", UUID.class)),
            Objects.requireNonNull(rs.getObject("buyer_id", UUID.class)),
            rs.getObject("order_id", UUID.class),
            Objects.requireNonNull(rs.getString("buyer_display_name")),
            rs.getString("variant_label"),
            rs.getInt("stars"),
            rs.getString("comment"),
            readStringArray(rs, "image_keys"),
            rs.getString("video_key"),
            readStringArray(rs, "tags"),
            rs.getBoolean("is_anonymous"),
            rs.getInt("helpful_count"),
            rs.getString("seller_reply"),
            readNullableInstant(rs, "replied_at"),
            rs.getBoolean("is_reported"),
            rs.getString("report_reason"),
            readNullableInstant(rs, "edited_at"),
            Objects.requireNonNull(readNullableInstant(rs, "created_at")),
            Objects.requireNonNull(readNullableInstant(rs, "updated_at")),
            false
        );
    }

    private static String[] readStringArray(ResultSet rs, String column) throws SQLException {
        var array = rs.getArray(column);
        if (array == null) {
            return new String[0];
        }
        var raw = array.getArray();
        if (raw instanceof String[] strings) {
            return strings;
        }
        if (raw instanceof Object[] objects) {
            var result = new String[objects.length];
            for (var i = 0; i < objects.length; i++) {
                result[i] = String.valueOf(objects[i]);
            }
            return result;
        }
        return new String[0];
    }

    private static @Nullable Instant readNullableInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}

