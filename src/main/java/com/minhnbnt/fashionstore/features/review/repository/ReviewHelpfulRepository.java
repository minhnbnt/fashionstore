package com.minhnbnt.fashionstore.features.review.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Thao tác trực tiếp trên bảng review_helpful (khóa chính ghép review_id, user_id).
 */
@Repository
@RequiredArgsConstructor
public class ReviewHelpfulRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Chèn lượt thích nếu chưa tồn tại. Trả true nếu vừa thêm mới, false nếu đã thích trước đó.
     */
    public boolean insertIfAbsent(UUID reviewId, UUID userId) {
        var params = new MapSqlParameterSource()
            .addValue("reviewId", reviewId)
            .addValue("userId", userId);
        var inserted = jdbc.update(
            """
            INSERT INTO review_helpful (review_id, user_id)
            VALUES (:reviewId, :userId)
            ON CONFLICT (review_id, user_id) DO NOTHING
            """,
            params
        );
        return inserted > 0;
    }

    /**
     * Xóa lượt thích của user cho review. Trả true nếu có dòng bị xóa.
     */
    public boolean delete(UUID reviewId, UUID userId) {
        var params = new MapSqlParameterSource()
            .addValue("reviewId", reviewId)
            .addValue("userId", userId);
        var deleted = jdbc.update(
            """
            DELETE FROM review_helpful
            WHERE review_id = :reviewId AND user_id = :userId
            """,
            params
        );
        return deleted > 0;
    }

    /**
     * Batch trả về các reviewId mà user đã đánh dấu hữu ích trong danh sách cần hiển thị.
     */
    public Set<UUID> findHelpfulReviewIds(UUID userId, Collection<UUID> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Set.of();
        }
        var params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("reviewIds", reviewIds);
        var ids = jdbc.queryForList(
            """
            SELECT review_id
            FROM review_helpful
            WHERE user_id = :userId
              AND review_id IN (:reviewIds)
            """,
            params,
            UUID.class
        );
        return new HashSet<>(ids);
    }
}

