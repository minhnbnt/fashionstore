package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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
        return new OrderItem(
            UUID.randomUUID(),
            orderId,
            productId,
            variantId,
            productName,
            variantLabel,
            sku,
            unitPrice,
            quantity,
            unitPrice * quantity,
            true
        );
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
