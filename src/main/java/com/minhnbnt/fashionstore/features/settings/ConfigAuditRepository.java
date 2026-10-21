package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.dto.settings.ConfigAuditResponse;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

/**
 * Lịch sử thay đổi cấu hình của admin (append-only, không có entity).
 */
@Repository
@RequiredArgsConstructor
public class ConfigAuditRepository {

    private final JdbcClient jdbc;

    public void insert(
        ConfigAuditEntity entity,
        String entityKey,
        String action,
        @Nullable String oldJson,
        @Nullable String newJson,
        UUID changedBy
    ) {
        jdbc.sql("""
                INSERT INTO admin_config_audit (id, entity, entity_key, action, old_value, new_value, changed_by)
                VALUES (:id, :entity, :key, :action, CAST(:old AS jsonb), CAST(:new AS jsonb), :by)
                """)
            .param("id", UUID.randomUUID())
            .param("entity", entity.name())
            .param("key", entityKey)
            .param("action", action)
            .param("old", oldJson, Types.VARCHAR)
            .param("new", newJson, Types.VARCHAR)
            .param("by", changedBy)
            .update();
    }

    public List<ConfigAuditResponse> search(@Nullable ConfigAuditEntity entity, int page, int size) {
        return jdbc.sql("""
                SELECT id, entity, entity_key, action, old_value::text AS old_value, new_value::text AS new_value,
                       changed_by, changed_at
                FROM admin_config_audit
                WHERE (CAST(:entity AS text) IS NULL OR entity = :entity)
                ORDER BY changed_at DESC, id
                LIMIT :limit OFFSET :offset
                """)
            .param("entity", nameOf(entity), Types.VARCHAR)
            .param("limit", size)
            .param("offset", (long) page * size)
            .query((rs, rowNum) -> toResponse(rs))
            .list();
    }

    public long count(@Nullable ConfigAuditEntity entity) {
        return jdbc.sql("SELECT count(*) FROM admin_config_audit WHERE (CAST(:entity AS text) IS NULL OR entity = :entity)")
            .param("entity", nameOf(entity), Types.VARCHAR)
            .query(Long.class)
            .single();
    }

    private static @Nullable String nameOf(@Nullable ConfigAuditEntity entity) {
        if (entity == null) {
            return null;
        }

        return entity.name();
    }

    private static ConfigAuditResponse toResponse(ResultSet rs) throws SQLException {
        return new ConfigAuditResponse(
            rs.getObject("id", UUID.class),
            ConfigAuditEntity.valueOf(rs.getString("entity")),
            rs.getString("entity_key"),
            rs.getString("action"),
            rs.getString("old_value"),
            rs.getString("new_value"),
            rs.getObject("changed_by", UUID.class),
            rs.getTimestamp("changed_at").toInstant()
        );
    }
}
