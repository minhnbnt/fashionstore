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

@Table("shop_role")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopRole implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private String name;

    private String[] permissions;

    private boolean isSystem;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static ShopRole create(UUID shopId, String name, String[] permissions, boolean isSystem) {
        return new ShopRole(UUID.randomUUID(), shopId, name, permissions, isSystem, Instant.now(), true);
    }

    public void update(String name, String[] permissions) {
        this.name = name;
        this.permissions = permissions;
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
