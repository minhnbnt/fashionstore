package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    private @Nullable String videoKey;

    private @Nullable String garmentKey;

    private boolean tryOnEnabled;

    private @Nullable String material;

    private @Nullable String origin;

    private @Nullable String careInstructions;

    private @Nullable String sizeGuide;

    private @Nullable Integer weightGrams;

    private @Nullable Integer lengthCm;

    private @Nullable Integer widthCm;

    private @Nullable Integer heightCm;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static Product create(
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        ProductStatus initialStatus,
        @Nullable String videoKey,
        @Nullable String garmentKey,
        boolean tryOnEnabled,
        @Nullable String material,
        @Nullable String origin,
        @Nullable String careInstructions,
        @Nullable String sizeGuide,
        @Nullable Integer weightGrams,
        @Nullable Integer lengthCm,
        @Nullable Integer widthCm,
        @Nullable Integer heightCm,
        @Nullable UUID createdBy
    ) {
        var now = Instant.now();
        return new Product(
            UUID.randomUUID(),
            shopId,
            categoryId,
            name,
            slug,
            description,
            initialStatus,
            null,
            null,
            createdBy,
            videoKey,
            garmentKey,
            tryOnEnabled,
            material,
            origin,
            careInstructions,
            sizeGuide,
            weightGrams,
            lengthCm,
            widthCm,
            heightCm,
            now,
            now,
            true

        );
    }

    public static Product create(
        UUID shopId,
        @Nullable UUID categoryId,
        String name,
        String slug,
        String description,
        @Nullable UUID createdBy
    ) {
        return create(
            shopId, categoryId, name, slug, description,
            ProductStatus.ACTIVE,
            null, null, false, null, null, null, null, null, null, null, null,
            createdBy
        );
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
        slug = slug + "-del-" + UUID.randomUUID().toString().substring(0, 8);
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

    /**
     * Admin phê duyệt sản phẩm mới từ PENDING sang ACTIVE.
     */
    public void approve() {
        if (status != ProductStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending products can be approved");
        }
        status = ProductStatus.ACTIVE;
        statusReason = null;
        violationType = null;
        updatedAt = Instant.now();
    }

    /**
     * Admin từ chối phê duyệt sản phẩm mới.
     */
    public void reject(String reason) {
        if (status != ProductStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending products can be rejected");
        }
        status = ProductStatus.BANNED;
        violationType = ProductViolationType.OTHER;
        statusReason = reason;
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

    public void updateAll(
        @Nullable String name,
        @Nullable String description,
        @Nullable UUID categoryId,
        @Nullable String videoKey,
        @Nullable String garmentKey,
        @Nullable Boolean tryOnEnabled,
        @Nullable String material,
        @Nullable String origin,
        @Nullable String careInstructions,
        @Nullable String sizeGuide,
        @Nullable Integer weightGrams,
        @Nullable Integer lengthCm,
        @Nullable Integer widthCm,
        @Nullable Integer heightCm
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
        if (videoKey != null) {
            this.videoKey = videoKey;
        }
        if (garmentKey != null) {
            this.garmentKey = garmentKey;
        }
        if (tryOnEnabled != null) {
            this.tryOnEnabled = tryOnEnabled;
        }
        if (material != null) {
            this.material = material;
        }
        if (origin != null) {
            this.origin = origin;
        }
        if (careInstructions != null) {
            this.careInstructions = careInstructions;
        }
        if (sizeGuide != null) {
            this.sizeGuide = sizeGuide;
        }
        if (weightGrams != null) {
            this.weightGrams = weightGrams;
        }
        if (lengthCm != null) {
            this.lengthCm = lengthCm;
        }
        if (widthCm != null) {
            this.widthCm = widthCm;
        }
        if (heightCm != null) {
            this.heightCm = heightCm;
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
