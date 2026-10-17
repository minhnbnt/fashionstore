package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Table("product")
@Getter
public class Product implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private @Nullable UUID categoryId;

    private String name;

    private String slug;

    private String description;

    private ProductStatus status;

    private @Nullable ProductViolationType violationType;

    private @Nullable String statusReason;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public Product(
        UUID id,
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        ProductStatus status,
        @Nullable ProductViolationType violationType,
        @Nullable String statusReason,
        @Nullable UUID createdBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.categoryId = categoryId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.status = status;
        this.violationType = violationType;
        this.statusReason = statusReason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Product create(
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        @Nullable UUID createdBy
    ) {
        var now = Instant.now();
        var product = new Product(
            UUID.randomUUID(),
            shopId,
            categoryId,
            name,
            slug,
            description,
            ProductStatus.ACTIVE,
            null,
            null,
            createdBy,
            now,
            now
        );
        product.newRecord = true;
        return product;
    }

    public void hide() {
        if (status != ProductStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active products can be hidden");
        }
        status = ProductStatus.HIDDEN;
        updatedAt = Instant.now();
    }

    public void activate() {
        if (status == ProductStatus.BANNED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Product was banned for a policy violation and cannot be activated"
            );
        }
        if (status != ProductStatus.HIDDEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only hidden products can be activated");
        }
        status = ProductStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public void delete() {
        if (status == ProductStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is already deleted");
        }
        status = ProductStatus.DELETED;
        updatedAt = Instant.now();
    }

    /**
     * Admin gỡ sản phẩm vi phạm. Gỡ được cả hàng đang ẩn để shop không tự bật lại.
     */
    public void ban(ProductViolationType type, String reason) {
        if (status == ProductStatus.BANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is already banned");
        }
        if (status == ProductStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted products cannot be banned");
        }
        status = ProductStatus.BANNED;
        violationType = type;
        statusReason = reason;
        updatedAt = Instant.now();
    }

    /**
     * Khôi phục về HIDDEN, không về thẳng ACTIVE: shop kiểm tra lại rồi tự đăng bán.
     */
    public void restore() {
        if (status != ProductStatus.BANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only banned products can be restored");
        }
        status = ProductStatus.HIDDEN;
        violationType = null;
        statusReason = null;
        updatedAt = Instant.now();
    }

    public void updateDetails(
        @Nullable String name,
        @Nullable String description,
        @Nullable UUID categoryId
    ) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
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
