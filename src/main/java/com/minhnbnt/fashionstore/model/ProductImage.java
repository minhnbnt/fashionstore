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

@Table("product_image")
@Getter
public class ProductImage implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private @Nullable UUID variantId;

    private String s3Key;

    private int sortOrder;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ProductImage(
        UUID id,
        UUID productId,
        @Nullable UUID variantId,
        String s3Key,
        int sortOrder,
        Instant createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.variantId = variantId;
        this.s3Key = s3Key;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ProductImage create(
        UUID productId,
        @Nullable UUID variantId,
        String s3Key,
        int sortOrder
    ) {
        var image = new ProductImage(
            UUID.randomUUID(),
            productId,
            variantId,
            s3Key,
            sortOrder,
            Instant.now()
        );
        image.newRecord = true;
        return image;
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
