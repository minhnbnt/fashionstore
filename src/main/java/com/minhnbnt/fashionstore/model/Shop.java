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

@Table("shop")
@Getter
public class Shop implements Persistable<UUID> {

    @Id
    private UUID id;

    private String name;

    private String slug;

    private String description;

    private @Nullable String logoKey;

    private @Nullable String bannerKey;

    private ShopStatus status;

    private @Nullable String statusReason;

    private @Nullable UUID approvedBy;

    private @Nullable Instant approvedAt;

    private Instant submittedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public Shop(
        UUID id,
        String name,
        String slug,
        String description,
        @Nullable String logoKey,
        @Nullable String bannerKey,
        ShopStatus status,
        @Nullable String statusReason,
        @Nullable UUID approvedBy,
        @Nullable Instant approvedAt,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.logoKey = logoKey;
        this.bannerKey = bannerKey;
        this.status = status;
        this.statusReason = statusReason;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.submittedAt = submittedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Shop create(
        String name,
        String slug,
        String description,
        @Nullable String logoKey,
        @Nullable String bannerKey
    ) {
        var now = Instant.now();
        var shop = new Shop(
            UUID.randomUUID(), name, slug, description, logoKey, bannerKey,
            ShopStatus.PENDING, null, null, null, now, now, now
        );
        shop.newRecord = true;
        return shop;
    }

    public void approve(UUID adminId) {
        if (status != ShopStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending shops can be approved");
        }
        status = ShopStatus.APPROVED;
        statusReason = null;
        approvedBy = adminId;
        approvedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void reject(String reason) {
        if (status != ShopStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending shops can be rejected");
        }
        status = ShopStatus.REJECTED;
        statusReason = reason;
        updatedAt = Instant.now();
    }

    /**
     * Owner sửa hồ sơ xong gửi duyệt lại, xếp cuối hàng đợi theo submittedAt mới.
     */
    public void resubmit() {
        if (status != ShopStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only rejected shops can be resubmitted");
        }
        status = ShopStatus.PENDING;
        statusReason = null;
        submittedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if (status != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved shops can be suspended");
        }
        status = ShopStatus.SUSPENDED;
        statusReason = reason;
        updatedAt = Instant.now();
    }

    public void reinstate() {
        if (status != ShopStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only suspended shops can be reinstated");
        }
        status = ShopStatus.APPROVED;
        statusReason = null;
        updatedAt = Instant.now();
    }

    public void updateDetails(
        @Nullable String name,
        @Nullable String description,
        @Nullable String logoKey,
        @Nullable String bannerKey
    ) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (logoKey != null) {
            this.logoKey = logoKey;
        }
        if (bannerKey != null) {
            this.bannerKey = bannerKey;
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
