package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class Address implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private String recipientName;

    private String phoneNumber;

    private String fullAddress;

    private AddressLabel label;

    @Column("is_default")
    private boolean defaultAddress;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static Address create(
        UUID userId,
        String recipientName,
        String phoneNumber,
        String fullAddress,
        AddressLabel label,
        boolean defaultAddress
    ) {
        var now = Instant.now();

        return new Address(
            UUID.randomUUID(),
            userId,
            recipientName,
            phoneNumber,
            fullAddress,
            label,
            defaultAddress,
            now, now,
            true
        );
    }

    public void updateDetails(String recipientName, String phoneNumber, String fullAddress, AddressLabel label) {
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.fullAddress = fullAddress;
        this.label = label;
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

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
