package com.minhnbnt.fashionstore.model;

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

    @PersistenceCreator
    public ShopRole(
        UUID id,
        UUID shopId,
        String name,
        String[] permissions,
        boolean isSystem,
        Instant createdAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.name = name;
        this.permissions = permissions;
        this.isSystem = isSystem;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ShopRole create(
        UUID shopId,
        String name,
        String[] permissions,
        boolean isSystem
    ) {
        var role = new ShopRole(
            UUID.randomUUID(),
            shopId,
            name,
            permissions,
            isSystem,
            Instant.now()
        );
        role.newRecord = true;
        return role;
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
