package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("wishlist_item")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static WishlistItem create(UUID buyerId, UUID productId) {
        var now = Instant.now();
        return new WishlistItem(UUID.randomUUID(), buyerId, productId, now, now, true);
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
