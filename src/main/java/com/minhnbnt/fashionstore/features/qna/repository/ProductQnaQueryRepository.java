package com.minhnbnt.fashionstore.features.qna.repository;

import com.minhnbnt.fashionstore.dto.qna.SellerQnaProductOptionResponse;
import com.minhnbnt.fashionstore.model.ProductQuestion;
import com.minhnbnt.fashionstore.model.ProductQuestionSort;
import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
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
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductQnaQueryRepository {

    private final JdbcClient jdbc;

    public record SellerQuestionRow(
        ProductQuestion question,
        String productName,
        long answerCount,
        long shopAnswerCount
    ) {
    }

    public record SellerQnaStatsRow(
        long totalQuestions,
        long unansweredCount,
        long answeredCount,
        long closedCount,
        long shopRepliedCount
    ) {
    }

    public static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    public List<ProductQuestion> findProductQuestions(
        UUID productId,
        @Nullable String keyword,
        int page,
        int size
    ) {
        var targetPage = Math.max(0, page);
        var targetSize = Math.max(1, size);
        var offset = (long) targetPage * targetSize;

        var sql = new StringBuilder("""
            SELECT q.id,
                   q.code,
                   q.product_id,
                   q.shop_id,
                   q.asker_id,
                   q.asker_display_name,
                   q.content,
                   q.status,
                   q.closed_at,
                   q.closed_by,
                   q.edited_at,
                   q.created_at,
                   q.updated_at
            FROM product_question q
            WHERE q.product_id = :productId
            """);

        var params = new LinkedHashMap<String, Object>();
        params.put("productId", productId);

        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                 AND (
                     q.content ILIKE :pattern ESCAPE '\\'
                     OR EXISTS (
                         SELECT 1 FROM product_answer a
                         WHERE a.question_id = q.id AND a.content ILIKE :pattern ESCAPE '\\'
                     )
                 )
                """);
            params.put("pattern", "%" + escapeLike(keyword.trim()) + "%");
        }

        sql.append(" ORDER BY q.created_at DESC, q.id DESC LIMIT :limit OFFSET :offset");
        params.put("limit", targetSize);
        params.put("offset", offset);

        var spec = jdbc.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query((rs, rowNum) -> mapProductQuestion(rs)).list();
    }

    public long countProductQuestions(UUID productId, @Nullable String keyword) {
        var sql = new StringBuilder("""
            SELECT count(*)
            FROM product_question q
            WHERE q.product_id = :productId
            """);

        var params = new LinkedHashMap<String, Object>();
        params.put("productId", productId);

        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                 AND (
                     q.content ILIKE :pattern ESCAPE '\\'
                     OR EXISTS (
                         SELECT 1 FROM product_answer a
                         WHERE a.question_id = q.id AND a.content ILIKE :pattern ESCAPE '\\'
                     )
                 )
                """);
            params.put("pattern", "%" + escapeLike(keyword.trim()) + "%");
        }

        var spec = jdbc.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query(Long.class).single();
    }

    public List<SellerQuestionRow> findShopQuestions(
        UUID shopId,
        @Nullable ProductQuestionStatus status,
        @Nullable UUID productId,
        @Nullable Instant from,
        @Nullable Instant to,
        @Nullable String keyword,
        ProductQuestionSort sort,
        int page,
        int size
    ) {
        var targetPage = Math.max(0, page);
        var targetSize = Math.max(1, size);
        var offset = (long) targetPage * targetSize;

        var sql = new StringBuilder("""
            SELECT q.id,
                   q.code,
                   q.product_id,
                   q.shop_id,
                   q.asker_id,
                   q.asker_display_name,
                   q.content,
                   q.status,
                   q.closed_at,
                   q.closed_by,
                   q.edited_at,
                   q.created_at,
                   q.updated_at,
                   p.name AS product_name,
                   (SELECT COUNT(*) FROM product_answer a WHERE a.question_id = q.id) AS answer_count,
                   (SELECT COUNT(*) FROM product_answer a
                    WHERE a.question_id = q.id AND a.author_role = 'SHOP') AS shop_answer_count
            FROM product_question q
            JOIN product p ON p.id = q.product_id
            WHERE q.shop_id = :shopId
            """);

        var params = new LinkedHashMap<String, Object>();
        params.put("shopId", shopId);

        if (status != null) {
            sql.append(" AND q.status = :status");
            params.put("status", status.name());
        }
        if (productId != null) {
            sql.append(" AND q.product_id = :productId");
            params.put("productId", productId);
        }
        if (from != null) {
            sql.append(" AND q.created_at >= :from");
            params.put("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND q.created_at < :to");
            params.put("to", Timestamp.from(to));
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                 AND (
                     p.name ILIKE :pattern ESCAPE '\\'
                     OR p.slug ILIKE :pattern ESCAPE '\\'
                     OR p.id::text ILIKE :pattern ESCAPE '\\'
                     OR q.code ILIKE :pattern ESCAPE '\\'
                     OR q.asker_display_name ILIKE :pattern ESCAPE '\\'
                     OR q.content ILIKE :pattern ESCAPE '\\'
                     OR EXISTS (
                         SELECT 1 FROM product_variant v
                         WHERE v.product_id = p.id AND v.sku ILIKE :pattern ESCAPE '\\'
                     )
                 )
                """);
            params.put("pattern", "%" + escapeLike(keyword.trim()) + "%");
        }

        String orderBy;
        if (sort == ProductQuestionSort.OLDEST) {
            orderBy = " ORDER BY q.created_at ASC, q.id ASC";
        } else {
            orderBy = " ORDER BY q.created_at DESC, q.id DESC";
        }
        sql.append(orderBy).append(" LIMIT :limit OFFSET :offset");
        params.put("limit", targetSize);
        params.put("offset", offset);

        var spec = jdbc.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query(ProductQnaQueryRepository::mapSellerQuestionRow).list();
    }

    public long countShopQuestions(
        UUID shopId,
        @Nullable ProductQuestionStatus status,
        @Nullable UUID productId,
        @Nullable Instant from,
        @Nullable Instant to,
        @Nullable String keyword
    ) {
        var sql = new StringBuilder("""
            SELECT count(*)
            FROM product_question q
            JOIN product p ON p.id = q.product_id
            WHERE q.shop_id = :shopId
            """);

        var params = new LinkedHashMap<String, Object>();
        params.put("shopId", shopId);

        if (status != null) {
            sql.append(" AND q.status = :status");
            params.put("status", status.name());
        }
        if (productId != null) {
            sql.append(" AND q.product_id = :productId");
            params.put("productId", productId);
        }
        if (from != null) {
            sql.append(" AND q.created_at >= :from");
            params.put("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND q.created_at < :to");
            params.put("to", Timestamp.from(to));
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                 AND (
                     p.name ILIKE :pattern ESCAPE '\\'
                     OR p.slug ILIKE :pattern ESCAPE '\\'
                     OR p.id::text ILIKE :pattern ESCAPE '\\'
                     OR q.code ILIKE :pattern ESCAPE '\\'
                     OR q.asker_display_name ILIKE :pattern ESCAPE '\\'
                     OR q.content ILIKE :pattern ESCAPE '\\'
                     OR EXISTS (
                         SELECT 1 FROM product_variant v
                         WHERE v.product_id = p.id AND v.sku ILIKE :pattern ESCAPE '\\'
                     )
                 )
                """);
            params.put("pattern", "%" + escapeLike(keyword.trim()) + "%");
        }

        var spec = jdbc.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query(Long.class).single();
    }

    /**
     * Đánh dấu ANSWERED chỉ khi question chưa bị đóng. Trả về số row bị update:
     * 0 nghĩa là status đã chuyển CLOSED trước khi câu UPDATE này chạy.
     */
    public int markAnsweredIfOpen(UUID questionId) {
        return jdbc.sql("""
            UPDATE product_question
            SET status = 'ANSWERED', updated_at = now()
            WHERE id = :id AND status <> 'CLOSED'
            """)
            .param("id", questionId)
            .update();
    }

    /**
     * Khoá row question rồi mới recompute status, chặn race 2 luồng xoá answer
     * SHOP cuối cùng cùng thấy answer của nhau (chưa commit) và cùng bỏ revert.
     */
    public int markUnansweredIfNoShopAnswer(UUID questionId) {
        return jdbc.sql("""
            WITH locked AS (
                SELECT id FROM product_question WHERE id = :id FOR UPDATE
            )
            UPDATE product_question q
            SET status = 'UNANSWERED', updated_at = now()
            FROM locked
            WHERE q.id = locked.id
              AND q.status = 'ANSWERED'
              AND NOT EXISTS (
                  SELECT 1 FROM product_answer a
                  WHERE a.question_id = q.id AND a.author_role = 'SHOP'
              )
            """)
            .param("id", questionId)
            .update();
    }

    public Optional<SellerQuestionRow> findShopQuestionRowById(UUID questionId) {
        var sql = """
            SELECT q.id,
                   q.code,
                   q.product_id,
                   q.shop_id,
                   q.asker_id,
                   q.asker_display_name,
                   q.content,
                   q.status,
                   q.closed_at,
                   q.closed_by,
                   q.edited_at,
                   q.created_at,
                   q.updated_at,
                   p.name AS product_name,
                   (SELECT COUNT(*) FROM product_answer a WHERE a.question_id = q.id) AS answer_count,
                   (SELECT COUNT(*) FROM product_answer a
                    WHERE a.question_id = q.id AND a.author_role = 'SHOP') AS shop_answer_count
            FROM product_question q
            JOIN product p ON p.id = q.product_id
            WHERE q.id = :questionId
            """;

        return jdbc.sql(sql)
            .param("questionId", questionId)
            .query(ProductQnaQueryRepository::mapSellerQuestionRow)
            .optional();
    }

    public List<SellerQnaProductOptionResponse> findShopQnaProductOptions(UUID shopId) {
        var sql = """
            SELECT p.id,
                   p.name,
                   COUNT(q.id) AS question_count
            FROM product p
            LEFT JOIN product_question q ON q.product_id = p.id
            WHERE p.shop_id = :shopId
              AND (p.status <> 'DELETED' OR q.id IS NOT NULL)
            GROUP BY p.id, p.name
            ORDER BY question_count DESC, p.name ASC
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new SellerQnaProductOptionResponse(
                Objects.requireNonNull(rs.getObject("id", UUID.class)),
                Objects.requireNonNull(rs.getString("name")),
                rs.getLong("question_count")
            ))
            .list();
    }

    public SellerQnaStatsRow summarizeShopQna(UUID shopId) {
        var sql = """
            SELECT
                COUNT(*) AS total_questions,
                COUNT(*) FILTER (WHERE q.status = 'UNANSWERED') AS unanswered_count,
                COUNT(*) FILTER (WHERE q.status = 'ANSWERED') AS answered_count,
                COUNT(*) FILTER (WHERE q.status = 'CLOSED') AS closed_count,
                COUNT(*) FILTER (WHERE EXISTS (
                    SELECT 1 FROM product_answer a WHERE a.question_id = q.id AND a.author_role = 'SHOP'
                )) AS shop_replied_count
            FROM product_question q
            WHERE q.shop_id = :shopId
            """;

        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query((rs, rowNum) -> new SellerQnaStatsRow(
                rs.getLong("total_questions"),
                rs.getLong("unanswered_count"),
                rs.getLong("answered_count"),
                rs.getLong("closed_count"),
                rs.getLong("shop_replied_count")
            ))
            .single();
    }

    private static ProductQuestion mapProductQuestion(ResultSet rs) throws SQLException {
        return new ProductQuestion(
            Objects.requireNonNull(rs.getObject("id", UUID.class)),
            Objects.requireNonNull(rs.getString("code")),
            Objects.requireNonNull(rs.getObject("product_id", UUID.class)),
            Objects.requireNonNull(rs.getObject("shop_id", UUID.class)),
            Objects.requireNonNull(rs.getObject("asker_id", UUID.class)),
            Objects.requireNonNull(rs.getString("asker_display_name")),
            Objects.requireNonNull(rs.getString("content")),
            ProductQuestionStatus.valueOf(Objects.requireNonNull(rs.getString("status"))),
            readNullableInstant(rs, "closed_at"),
            rs.getObject("closed_by", UUID.class),
            readNullableInstant(rs, "edited_at"),
            Objects.requireNonNull(readNullableInstant(rs, "created_at")),
            Objects.requireNonNull(readNullableInstant(rs, "updated_at")),
            false
        );
    }

    private static SellerQuestionRow mapSellerQuestionRow(ResultSet rs, int rowNum) throws SQLException {
        return new SellerQuestionRow(
            mapProductQuestion(rs),
            Objects.requireNonNull(rs.getString("product_name")),
            rs.getLong("answer_count"),
            rs.getLong("shop_answer_count")
        );
    }

    private static @Nullable Instant readNullableInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
