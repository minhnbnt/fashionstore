package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.dto.settings.ConfigAuditResponse;
import com.minhnbnt.fashionstore.model.AdminConfigAudit;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Lịch sử thay đổi cấu hình của admin (append-only, bảng admin_config_audit).
 * SQL tĩnh: filter rỗng xử lý bằng CAST(:entity AS text) IS NULL.
 */
public interface ConfigAuditRepository extends Repository<AdminConfigAudit, UUID> {

    /**
     * Append-only: id dòng audit do caller sinh, old/new là JSON dạng text cast sang jsonb.
     */
    @Modifying
    @Query("""
        INSERT INTO admin_config_audit (id, entity, entity_key, action, old_value, new_value, changed_by)
        VALUES (:id, :entity, :entityKey, :action, CAST(:oldValue AS jsonb), CAST(:newValue AS jsonb), :changedBy)
        """)
    void insert(
        UUID id,
        ConfigAuditEntity entity,
        String entityKey,
        String action,
        @Nullable String oldValue,
        @Nullable String newValue,
        UUID changedBy
    );

    /**
     * Giữ chữ ký cũ cho caller: tự sinh id dòng audit.
     */
    default void insert(
        ConfigAuditEntity entity,
        String entityKey,
        String action,
        @Nullable String oldJson,
        @Nullable String newJson,
        UUID changedBy
    ) {
        insert(UUID.randomUUID(), entity, entityKey, action, oldJson, newJson, changedBy);
    }

    /**
     * Bind enum trực tiếp qua named param không khẳng định được kiểu SQL, nên bridge qua name().
     */
    @Query("""
        SELECT id, entity, entity_key, action, old_value::text AS old_value, new_value::text AS new_value,
               changed_by, changed_at
        FROM admin_config_audit
        WHERE (CAST(:entity AS text) IS NULL OR entity = :entity)
        ORDER BY changed_at DESC, id
        LIMIT :limit OFFSET :offset
        """)
    List<ConfigAuditResponse> searchByEntityName(@Nullable String entity, int limit, long offset);

    default List<ConfigAuditResponse> search(@Nullable ConfigAuditEntity entity, int page, int size) {
        String name = null;
        if (entity != null) {
            name = entity.name();
        }

        return searchByEntityName(name, size, (long) page * size);
    }

    @Query("SELECT count(*) FROM admin_config_audit WHERE (CAST(:entity AS text) IS NULL OR entity = :entity)")
    long countByEntityName(@Nullable String entity);

    default long count(@Nullable ConfigAuditEntity entity) {
        String name = null;
        if (entity != null) {
            name = entity.name();
        }

        return countByEntityName(name);
    }
}
