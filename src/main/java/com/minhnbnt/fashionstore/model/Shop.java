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

    private @Nullable UUID approvedBy;

    private @Nullable Instant approvedAt;

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
        @Nullable UUID approvedBy,
        @Nullable Instant approvedAt,
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
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
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
            ShopStatus.PENDING, null, null, now, now
        );
        shop.newRecord = true;
        return shop;
    }

    public void approve(UUID adminId) {
        if (status != ShopStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending shops can be approved");
        }
        status = ShopStatus.APPROVED;
        approvedBy = adminId;
        approvedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void reject() {
        if (status != ShopStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending shops can be rejected");
        }
        status = ShopStatus.REJECTED;
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
