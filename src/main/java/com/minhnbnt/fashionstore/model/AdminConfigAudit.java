package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng lịch sử thay đổi cấu hình của admin (bảng admin_config_audit, append-only).
 * Chỉ làm domain type cho repository — đọc ra bằng DTO projection, không save/findById qua CRUD.
 */
@Table("admin_config_audit")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class AdminConfigAudit {

    @Id
    private final UUID id;

    private final ConfigAuditEntity entity;

    private final String entityKey;

    private final String action;

    private final @Nullable String oldValue;

    private final @Nullable String newValue;

    private final @Nullable UUID changedBy;

    private final Instant changedAt;
}
