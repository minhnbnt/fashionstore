package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("product_moderation_history")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ProductModerationHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private String fromStatus;

    private String toStatus;

    private @Nullable String violationType;

    private @Nullable UUID changedBy;

    private String reason;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static ProductModerationHistory record(
        UUID productId,
        ProductStatus from,
        ProductStatus to,
        @Nullable ProductViolationType violationType,
        UUID changedBy,
        String reason
    ) {
        String violationName = null;
        if (violationType != null) {
            violationName = violationType.name();
        }
        return new ProductModerationHistory(
            UUID.randomUUID(),
            productId,
            from.name(),
            to.name(),
            violationName,
            changedBy,
            reason,
            Instant.now(),
            true
        );
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
