package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("order_item")
@Getter
public class OrderItem implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID orderId;

    private @Nullable UUID productId;

    private @Nullable UUID variantId;

    private String productName;

    private String variantLabel;

    private String sku;

    private long unitPrice;

    private int quantity;

    private long lineTotal;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public OrderItem(
        UUID id,
        UUID orderId,
        @Nullable UUID productId,
        @Nullable UUID variantId,
        String productName,
        String variantLabel,
        String sku,
        long unitPrice,
        int quantity,
        long lineTotal
    ) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.variantLabel = variantLabel;
        this.sku = sku;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
        this.newRecord = false;
    }

    public static OrderItem snapshot(
        UUID orderId,
        @Nullable UUID productId,
        @Nullable UUID variantId,
        String productName,
        String variantLabel,
        String sku,
        long unitPrice,
        int quantity
    ) {
        var item = new OrderItem(
            UUID.randomUUID(),
            orderId,
            productId,
            variantId,
            productName,
            variantLabel,
            sku,
            unitPrice,
            quantity,
            unitPrice * quantity
        );
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
