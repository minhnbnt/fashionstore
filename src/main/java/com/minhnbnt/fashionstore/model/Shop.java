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

    private @Nullable String phone;

    private @Nullable String address;

    private @Nullable String returnPolicy;

    private int processingDays;

    private String sellerType;

    private @Nullable String taxCode;

    private @Nullable String idNumber;

    private @Nullable String idDocKey;

    private @Nullable String businessCertKey;

    private @Nullable Double rating;

    private int ratingCount;

    private int followerCount;

    private @Nullable String bankName;

    private @Nullable String bankAccount;

    private @Nullable String bankHolder;

    private @Nullable String autoReplyText;

    private @Nullable String workingHours;

    private @Nullable String legalName;

    private @Nullable String idFrontKey;

    private @Nullable String idBackKey;

    private @Nullable String idSelfieKey;

    private @Nullable String idIssuedDate;

    private @Nullable String idIssuedPlace;

    private String kycStatus;

    private @Nullable String kycRejectionReason;

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
        @Nullable String phone,
        @Nullable String address,
        @Nullable String returnPolicy,
        int processingDays,
        String sellerType,
        @Nullable String taxCode,
        @Nullable String idNumber,
        @Nullable String idDocKey,
        @Nullable String businessCertKey,
        @Nullable Double rating,
        int ratingCount,
        int followerCount,
        @Nullable String bankName,
        @Nullable String bankAccount,
        @Nullable String bankHolder,
        @Nullable String autoReplyText,
        @Nullable String workingHours,
        @Nullable String legalName,
        @Nullable String idFrontKey,
        @Nullable String idBackKey,
        @Nullable String idSelfieKey,
        @Nullable String idIssuedDate,
        @Nullable String idIssuedPlace,
        @Nullable String kycStatus,
        @Nullable String kycRejectionReason,
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
        if (submittedAt != null) {
            this.submittedAt = submittedAt;
        } else if (createdAt != null) {
            this.submittedAt = createdAt;
        } else {
            this.submittedAt = Instant.now();
        }
        this.phone = phone;
        this.address = address;
        this.returnPolicy = returnPolicy;
        if (processingDays <= 0) {
            this.processingDays = 2;
        } else {
            this.processingDays = processingDays;
        }
        if (sellerType == null) {
            this.sellerType = "INDIVIDUAL";
        } else {
            this.sellerType = sellerType;
        }
        this.taxCode = taxCode;
        this.idNumber = idNumber;
        this.idDocKey = idDocKey;
        this.businessCertKey = businessCertKey;
        this.rating = rating;
        this.ratingCount = ratingCount;
        this.followerCount = followerCount;
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.bankHolder = bankHolder;
        this.autoReplyText = autoReplyText;
        this.workingHours = workingHours;
        this.legalName = legalName;
        this.idFrontKey = idFrontKey;
        this.idBackKey = idBackKey;
        this.idSelfieKey = idSelfieKey;
        this.idIssuedDate = idIssuedDate;
        this.idIssuedPlace = idIssuedPlace;
        if (kycStatus == null) {
            this.kycStatus = "PENDING_REVIEW";
        } else {
            this.kycStatus = kycStatus;
        }
        this.kycRejectionReason = kycRejectionReason;
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
        return create(
            name, slug, description, logoKey, bannerKey,
            null, null, "INDIVIDUAL", null, null,
            null, null, null, null, null,
            null, null, null, null, null
        );
    }

    public static Shop create(
        String name,
        String slug,
        String description,
        @Nullable String logoKey,
        @Nullable String bannerKey,
        @Nullable String phone,
        @Nullable String address,
        @Nullable String sellerType,
        @Nullable String legalName,
        @Nullable String idNumber,
        @Nullable String idIssuedDate,
        @Nullable String idIssuedPlace,
        @Nullable String taxCode,
        @Nullable String idFrontKey,
        @Nullable String idBackKey,
        @Nullable String idSelfieKey,
        @Nullable String businessCertKey,
        @Nullable String bankName,
        @Nullable String bankAccount,
        @Nullable String bankHolder
    ) {
        var now = Instant.now();
        var effectiveSellerType = "INDIVIDUAL";
        if (sellerType != null) {
            effectiveSellerType = sellerType;
        }
        var shop = new Shop(
            UUID.randomUUID(), name, slug, description, logoKey, bannerKey,
            ShopStatus.PENDING, null, null, null, now,
            phone, address, null, 2, effectiveSellerType,
            taxCode, idNumber, idFrontKey, businessCertKey,
            null, 0, 0,
            bankName, bankAccount, bankHolder,
            null, null,
            legalName, idFrontKey, idBackKey, idSelfieKey, idIssuedDate, idIssuedPlace,
            "PENDING_REVIEW", null,
            now, now
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
        kycStatus = "VERIFIED";
        approvedBy = adminId;
        approvedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void reject(@Nullable String reason) {
        if (status != ShopStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending shops can be rejected");
        }
        status = ShopStatus.REJECTED;
        statusReason = reason;
        kycStatus = "REJECTED";
        kycRejectionReason = reason;
        updatedAt = Instant.now();
    }

    public void reject() {
        reject(null);
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

    public void suspend(@Nullable String reason) {
        if (status != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved shops can be suspended");
        }
        status = ShopStatus.SUSPENDED;
        statusReason = reason;
        updatedAt = Instant.now();
    }

    public void suspend() {
        suspend(null);
    }

    public void reinstate() {
        if (status != ShopStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only suspended shops can be reinstated");
        }
        status = ShopStatus.APPROVED;
        statusReason = null;
        updatedAt = Instant.now();
    }

    public void unsuspend() {
        reinstate();
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

    public void updateProfile(
        @Nullable String phone,
        @Nullable String address,
        @Nullable String returnPolicy,
        @Nullable Integer processingDays,
        @Nullable String bankName,
        @Nullable String bankAccount,
        @Nullable String bankHolder,
        @Nullable String autoReplyText,
        @Nullable String workingHours
    ) {
        if (phone != null) {
            this.phone = phone;
        }
        if (address != null) {
            this.address = address;
        }
        if (returnPolicy != null) {
            this.returnPolicy = returnPolicy;
        }
        if (processingDays != null) {
            this.processingDays = processingDays;
        }
        if (bankName != null) {
            this.bankName = bankName;
        }
        if (bankAccount != null) {
            this.bankAccount = bankAccount;
        }
        if (bankHolder != null) {
            this.bankHolder = bankHolder;
        }
        if (autoReplyText != null) {
            this.autoReplyText = autoReplyText;
        }
        if (workingHours != null) {
            this.workingHours = workingHours;
        }
        updatedAt = Instant.now();
    }

    public void incrementFollowers() {
        this.followerCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementFollowers() {
        if (this.followerCount > 0) {
            this.followerCount--;
            this.updatedAt = Instant.now();
        }
    }

    public void updateRating(double newRating, int newRatingCount) {
        this.rating = newRating;
        this.ratingCount = newRatingCount;
        this.updatedAt = Instant.now();
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
