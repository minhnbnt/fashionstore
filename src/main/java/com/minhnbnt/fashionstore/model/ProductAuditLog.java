package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("product_audit_log")
@Getter
public class ProductAuditLog implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private UUID shopId;

    private @Nullable UUID changedBy;

    private String action;

    private String changesSummary;

    private @Nullable String oldValues;

    private @Nullable String newValues;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ProductAuditLog(
        UUID id,
        UUID productId,
        UUID shopId,
        @Nullable UUID changedBy,
        String action,
        String changesSummary,
        @Nullable String oldValues,
        @Nullable String newValues,
        Instant createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.shopId = shopId;
        this.changedBy = changedBy;
        this.action = action;
        this.changesSummary = changesSummary;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ProductAuditLog record(
        UUID productId,
        UUID shopId,
        @Nullable UUID changedBy,
        String action,
        String changesSummary,
        @Nullable String oldValues,
        @Nullable String newValues
    ) {
        var log = new ProductAuditLog(
            UUID.randomUUID(),
            productId,
            shopId,
            changedBy,
            action,
            changesSummary,
            oldValues,
            newValues,
            Instant.now()
        );
        log.newRecord = true;
        return log;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
