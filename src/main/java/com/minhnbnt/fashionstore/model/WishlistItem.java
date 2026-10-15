package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("wishlist_item")
@Getter
public class WishlistItem implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID buyerId;

    private UUID productId;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    @PersistenceCreator
    public WishlistItem(UUID id, UUID buyerId, UUID productId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.buyerId = buyerId;
        this.productId = productId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static WishlistItem create(UUID buyerId, UUID productId) {
        var now = Instant.now();
        var item = new WishlistItem(UUID.randomUUID(), buyerId, productId, now, now);
        item.newRecord = true;
        return item;
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
