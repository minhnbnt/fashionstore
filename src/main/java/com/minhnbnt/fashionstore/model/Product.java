package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Table("product")
@Getter
public class Product implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private @Nullable UUID categoryId;

    private String name;

    private String slug;

    private String description;

    private ProductStatus status;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public Product(
        UUID id,
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        ProductStatus status,
        @Nullable UUID createdBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.categoryId = categoryId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Product create(
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        @Nullable UUID createdBy
    ) {
        var now = Instant.now();
        var product = new Product(
            UUID.randomUUID(),
            shopId,
            categoryId,
            name,
            slug,
            description,
            ProductStatus.ACTIVE,
            createdBy,
            now,
            now
        );
        product.newRecord = true;
        return product;
    }

    public void hide() {
        if (status != ProductStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active products can be hidden");
        }
        status = ProductStatus.HIDDEN;
        updatedAt = Instant.now();
    }

    public void activate() {
        if (status != ProductStatus.HIDDEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only hidden products can be activated");
        }
        status = ProductStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public void updateDetails(
        @Nullable String name,
        @Nullable String description,
        @Nullable UUID categoryId
    ) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        updatedAt = Instant.now();
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
