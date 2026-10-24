package com.minhnbnt.fashionstore.features.notification;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final JdbcClient jdbc;

    public record NotificationRow(
        UUID id,
        String type,
        String title,
        String body,
        @Nullable String link,
        @Nullable Instant readAt,
        Instant createdAt
    ) {
    }

    public void insert(
        UUID userId,
        String type,
        String title,
        String body,
        @Nullable String link,
        @Nullable UUID refId
    ) {
        jdbc.sql("""
            INSERT INTO user_notification (id, user_id, type, title, body, link, ref_id)
            VALUES (:id, :user, :type, :title, :body, :link, :ref)
            """)
            .param("id", UUID.randomUUID())
            .param("user", userId)
            .param("type", type)
            .param("title", title)
            .param("body", body)
            .param("link", link)
            .param("ref", refId)
            .update();
    }

    public List<NotificationRow> findPage(UUID userId, int page, int size) {
        return jdbc.sql("""
            SELECT id, type, title, body, link, read_at, created_at
            FROM user_notification
            WHERE user_id = :user
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
            .param("user", userId)
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(NotificationRepository::toRow)
            .list();
    }

    public long countUnread(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM user_notification WHERE user_id = :user AND read_at IS NULL")
            .param("user", userId)
            .query(Long.class)
            .single();
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
        jdbc.sql("UPDATE user_notification SET read_at = now() WHERE user_id = :user AND read_at IS NULL")
            .param("user", userId)
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
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("link"),
            readAtInstant,
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
