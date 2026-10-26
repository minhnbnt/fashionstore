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

@Table("shop_visit_log")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopVisitLog implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private @Nullable UUID visitorId;

    private Instant visitedAt;

    @Transient
    private boolean newRecord;

    public static ShopVisitLog create(UUID shopId, @Nullable UUID visitorId) {
        return new ShopVisitLog(UUID.randomUUID(), shopId, visitorId, Instant.now(), true);
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
