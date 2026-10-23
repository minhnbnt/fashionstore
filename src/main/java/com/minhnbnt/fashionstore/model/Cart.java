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

@Table("cart")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class Cart implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID buyerId;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static Cart create(UUID buyerId) {
        var now = Instant.now();
        return new Cart(UUID.randomUUID(), buyerId, now, now, true);
    }

    public void touch() {
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
