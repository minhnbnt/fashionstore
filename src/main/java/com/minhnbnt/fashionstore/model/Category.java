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

@Table("category")
@Getter
public class Category implements Persistable<UUID> {

    @Id
    private UUID id;

    private String name;

    private String slug;

    private @Nullable UUID parentId;

    private int sortOrder;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public Category(
        UUID id,
        String name,
        String slug,
        @Nullable UUID parentId,
        int sortOrder,
        Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static Category seed(String name, String slug, int sortOrder) {
        var category = new Category(
            UUID.randomUUID(),
            name,
            slug,
            null,
            sortOrder,
            Instant.now()
        );
        category.newRecord = true;
        return category;
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
