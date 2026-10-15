package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
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

@Table("address")
@Getter
public class Address implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private @Nullable String houseNumber;

    private @Nullable String street;

    private @Nullable String ward;

    private @Nullable String district;

    private @Nullable String city;

    @Column("is_default")
    private boolean defaultAddress;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    @PersistenceCreator
    public Address(
        UUID id,
        UUID userId,
        @Nullable String houseNumber,
        @Nullable String street,
        @Nullable String ward,
        @Nullable String district,
        @Nullable String city,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.houseNumber = houseNumber;
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.defaultAddress = defaultAddress;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Address create(
        UUID userId,
        @Nullable String houseNumber,
        @Nullable String street,
        @Nullable String ward,
        @Nullable String district,
        @Nullable String city,
        boolean defaultAddress
    ) {
        var now = Instant.now();
        var address = new Address(
            UUID.randomUUID(),
            userId,
            houseNumber,
            street,
            ward,
            district,
            city,
            defaultAddress,
            now,
            now
        );
        address.newRecord = true;
        return address;
    }

    public void updateDetails(
        @Nullable String houseNumber,
        @Nullable String street,
        @Nullable String ward,
        @Nullable String district,
        @Nullable String city
    ) {
        if (!isEmpty(houseNumber)) {
            this.houseNumber = houseNumber;
        }
        if (!isEmpty(street)) {
            this.street = street;
        }
        if (!isEmpty(ward)) {
            this.ward = ward;
        }
        if (!isEmpty(district)) {
            this.district = district;
        }
        if (!isEmpty(city)) {
            this.city = city;
        }
        updatedAt = Instant.now();
    }

    public void makeDefault() {
        defaultAddress = true;
        updatedAt = Instant.now();
    }

    public void clearDefault() {
        defaultAddress = false;
        updatedAt = Instant.now();
    }

    private static boolean isEmpty(@Nullable String value) {
        if (value == null) {
            return true;
        }

        return value.isBlank();
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
