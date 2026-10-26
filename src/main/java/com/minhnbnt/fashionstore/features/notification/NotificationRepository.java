package com.minhnbnt.fashionstore.features.notification;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final JdbcClient jdbc;

    public record NotificationRow(
        UUID id,
        String category,
        String type,
        String title,
        String body,
        @Nullable String link,
        @Nullable Instant readAt,
        Instant createdAt
    ) {
    }

    public NotificationRow insert(
        UUID userId,
        NotificationCategory category,
        String type,
        String title,
        String body,
        @Nullable String link,
        @Nullable UUID refId
    ) {
        return jdbc.sql("""
            INSERT INTO user_notification (id, user_id, category, type, title, body, link, ref_id)
            VALUES (:id, :user, :category, :type, :title, :body, :link, :ref)
            RETURNING id, category, type, title, body, link, read_at, created_at
            """)
            .param("id", UUID.randomUUID())
            .param("user", userId)
            .param("category", category.name())
            .param("type", type)
            .param("title", title)
            .param("body", body)
            .param("link", link)
            .param("ref", refId)
            .query(NotificationRepository::toRow)
            .single();
    }

    public List<NotificationRow> findPage(UUID userId, int page, int size) {
        return findPage(userId, null, page, size);
    }

    public List<NotificationRow> findPage(
        UUID userId,
        @Nullable List<String> categories,
        int page,
        int size
    ) {
        var sql = new StringBuilder("""
            SELECT id, category, type, title, body, link, read_at, created_at
            FROM user_notification
            WHERE user_id = :user
            """);
        if (categories != null && !categories.isEmpty()) {
            sql.append(" AND category = ANY(:cats)");
        }
        sql.append("""
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """);

        var spec = jdbc.sql(sql.toString())
            .param("user", userId)
            .param("limit", size)
            .param("offset", (long) page * size);
        if (categories != null && !categories.isEmpty()) {
            spec.param("cats", categories.toArray(new String[0]));
        }
        return spec.query(NotificationRepository::toRow).list();
    }

    public long countUnread(UUID userId) {
        return countUnread(userId, null);
    }

    public long countUnread(UUID userId, @Nullable List<String> categories) {
        var sql = new StringBuilder("SELECT count(*) FROM user_notification WHERE user_id = :user AND read_at IS NULL");
        if (categories != null && !categories.isEmpty()) {
            sql.append(" AND category = ANY(:cats)");
        }
        var spec = jdbc.sql(sql.toString()).param("user", userId);
        if (categories != null && !categories.isEmpty()) {
            spec.param("cats", categories.toArray(new String[0]));
        }
        return spec.query(Long.class).single();
    }

    public Map<String, Long> countUnreadByCategory(UUID userId, @Nullable List<String> categories) {
        var sql = new StringBuilder("""
            SELECT category, count(*) AS cnt
            FROM user_notification
            WHERE user_id = :user AND read_at IS NULL
            """);
        if (categories != null && !categories.isEmpty()) {
            sql.append(" AND category = ANY(:cats)");
        }
        sql.append(" GROUP BY category");
        var spec = jdbc.sql(sql.toString()).param("user", userId);
        if (categories != null && !categories.isEmpty()) {
            spec.param("cats", categories.toArray(new String[0]));
        }
        var result = new HashMap<String, Long>();
        var rows = spec.query((rs, rowNum) -> Map.entry(rs.getString("category"), rs.getLong("cnt"))).list();
        for (var row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }

    public long countByUser(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM user_notification WHERE user_id = :user")
            .param("user", userId)
            .query(Long.class)
            .single();
    }

    public boolean exists(UUID userId, UUID id) {
        var count = jdbc.sql("SELECT count(*) FROM user_notification WHERE user_id = :user AND id = :id")
            .param("user", userId)
            .param("id", id)
            .query(Long.class)
            .single();
        return count > 0;
    }

    public void markRead(UUID userId, UUID id) {
        jdbc.sql("""
            UPDATE user_notification SET read_at = now()
            WHERE user_id = :user AND id = :id AND read_at IS NULL
            """)
            .param("user", userId)
            .param("id", id)
            .update();
    }

    public void markAllRead(UUID userId) {
        markAllRead(userId, null);
    }

    public void markAllRead(UUID userId, @Nullable List<String> categories) {
        var sql = new StringBuilder("UPDATE user_notification SET read_at = now() WHERE user_id = :user AND read_at IS NULL");
        if (categories != null && !categories.isEmpty()) {
            sql.append(" AND category = ANY(:cats)");
        }
        var spec = jdbc.sql(sql.toString()).param("user", userId);
        if (categories != null && !categories.isEmpty()) {
            spec.param("cats", categories.toArray(new String[0]));
        }
        spec.update();
    }

    public void delete(UUID userId, UUID id) {
        jdbc.sql("DELETE FROM user_notification WHERE user_id = :user AND id = :id")
            .param("user", userId)
            .param("id", id)
            .update();
    }

    public void deleteByRefId(UUID refId) {
        jdbc.sql("DELETE FROM user_notification WHERE ref_id = :ref")
            .param("ref", refId)
            .update();
    }

    private static NotificationRow toRow(ResultSet rs, int rowNum) throws SQLException {
        var readAt = rs.getObject("read_at", OffsetDateTime.class);
        Instant readAtInstant = null;
        if (readAt != null) {
            readAtInstant = readAt.toInstant();
        }

        return new NotificationRow(
            rs.getObject("id", UUID.class),
            rs.getString("category"),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("link"),
            readAtInstant,
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
