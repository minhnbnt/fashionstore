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

    private String recipientName;

    private String phoneNumber;

    private @Nullable String detailAddress;

    private @Nullable String houseNumber;

    private @Nullable String street;

    private @Nullable String ward;

    private @Nullable String district;

    private @Nullable String city;

    private AddressLabel label;

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
        String recipientName,
        String phoneNumber,
        @Nullable String detailAddress,
        @Nullable String houseNumber,
        @Nullable String street,
        @Nullable String ward,
        @Nullable String district,
        @Nullable String city,
        AddressLabel label,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.detailAddress = detailAddress;
        this.houseNumber = houseNumber;
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.label = label;
        this.defaultAddress = defaultAddress;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Address create(
        UUID userId,
        String recipientName,
        String phoneNumber,
        @Nullable String detailAddress,
        @Nullable String houseNumber,
        @Nullable String street,
        @Nullable String ward,
        @Nullable String district,
        @Nullable String city,
        AddressLabel label,
        boolean defaultAddress
    ) {
        var now = Instant.now();
        var address = new Address(
            UUID.randomUUID(),
            userId,
            recipientName,
            phoneNumber,
            detailAddress,
            houseNumber,
            street,
            ward,
            district,
            city,
            label,
            defaultAddress,
            now,
            now
        );
        address.newRecord = true;
        return address;
    }

    public void updateDetails(
        @Nullable String recipientName,
        @Nullable String phoneNumber,
        @Nullable String detailAddress,
        @Nullable String houseNumber,
        @Nullable String street,
        @Nullable String ward,
        @Nullable String district,
        @Nullable String city,
        @Nullable AddressLabel label
    ) {
        if (recipientName != null && !recipientName.isBlank()) {
            this.recipientName = recipientName.trim();
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            this.phoneNumber = phoneNumber.trim();
        }
        if (!isEmpty(detailAddress)) {
            this.detailAddress = detailAddress;
        }
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
        if (label != null) {
            this.label = label;
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
