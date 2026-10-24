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

@Table("category")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static Category seed(String name, String slug, int sortOrder) {
        return new Category(UUID.randomUUID(), name, slug, null, sortOrder, Instant.now(), true);
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
