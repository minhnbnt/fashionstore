package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("cart_item")
@Getter
public class CartItem implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID cartId;

    private UUID variantId;

    private int quantity;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public CartItem(UUID id, UUID cartId, UUID variantId, int quantity, Instant createdAt) {
        this.id = id;
        this.cartId = cartId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static CartItem create(UUID cartId, UUID variantId, int quantity) {
        var item = new CartItem(UUID.randomUUID(), cartId, variantId, quantity, Instant.now());
        item.newRecord = true;
        return item;
    }

    public void increaseQuantity(int amount) {
        quantity += amount;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
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
