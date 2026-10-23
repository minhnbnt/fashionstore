package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("product_variant")
@Getter
public class ProductVariant implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private UUID productId;

    private String sku;

    private String size;

    private String color;

    private long price;

    private @Nullable Long compareAtPrice;

    private int stockQuantity;

    private int lowStockThreshold;

    @Column("is_default")
    private boolean defaultVariant;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ProductVariant(
        UUID id,
        UUID shopId,
        UUID productId,
        String sku,
        String size,
        String color,
        long price,
        @Nullable Long compareAtPrice,
        int stockQuantity,
        int lowStockThreshold,
        boolean defaultVariant,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.productId = productId;
        this.sku = sku;
        this.size = size;
        this.color = color;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.stockQuantity = stockQuantity;
        if (lowStockThreshold <= 0) {
            this.lowStockThreshold = 5;
        } else {
            this.lowStockThreshold = lowStockThreshold;
        }
        this.defaultVariant = defaultVariant;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static ProductVariant create(
        UUID shopId,
        UUID productId,
        String sku,
        String size,
        String color,
        long price,
        @Nullable Long compareAtPrice,
        int stockQuantity,
        boolean defaultVariant
    ) {
        var now = Instant.now();
        var variant = new ProductVariant(
            UUID.randomUUID(),
            shopId,
            productId,
            sku,
            size,
            color,
            price,
            compareAtPrice,
            stockQuantity,
            5,
            defaultVariant,
            now,
            now
        );
        variant.newRecord = true;
        return variant;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
        this.updatedAt = Instant.now();
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
        this.updatedAt = Instant.now();
    }

    public void updatePricing(
        @Nullable Long price,
        @Nullable Long compareAtPrice,
        @Nullable Integer stockQuantity,
        @Nullable Boolean defaultVariant
    ) {
        if (price != null) {
            this.price = price;
        }
        if (compareAtPrice != null) {
            this.compareAtPrice = compareAtPrice;
        }
        if (stockQuantity != null) {
            this.stockQuantity = stockQuantity;
        }
        if (defaultVariant != null) {
            this.defaultVariant = defaultVariant;
        }
        updatedAt = Instant.now();
    }

    public void updateDetails(
        @Nullable String sku,
        @Nullable String size,
        @Nullable String color,
        @Nullable Long price,
        @Nullable Long compareAtPrice,
        @Nullable Integer stockQuantity,
        @Nullable Boolean defaultVariant
    ) {
        if (sku != null && !sku.isBlank()) {
            this.sku = sku;
        }
        if (size != null && !size.isBlank()) {
            this.size = size;
        }
        if (color != null && !color.isBlank()) {
            this.color = color;
        }
        updatePricing(price, compareAtPrice, stockQuantity, defaultVariant);
    }

    public void archiveSku(String suffix) {
        this.sku = this.sku + suffix;
        this.updatedAt = Instant.now();
    }

    public void unsetDefault() {
        this.defaultVariant = false;
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
