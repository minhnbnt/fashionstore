package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("product_garment")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ProductGarment implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private UUID uploadId;

    private int sortOrder;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static ProductGarment create(UUID productId, UUID uploadId, int sortOrder) {
        return new ProductGarment(UUID.randomUUID(), productId, uploadId, sortOrder, Instant.now(), true);
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
