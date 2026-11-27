package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Table("seller_settlement")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class SellerSettlement implements Persistable<UUID> {

    public static final int PLATFORM_FEE_PERCENT = 5;

    @Id
    private UUID id;

    private UUID shopId;

    private UUID orderId;

    private long grossAmount;

    private long platformFee;

    private long netAmount;

    private SettlementStatus status;

    private Instant holdUntil;

    private @Nullable Instant releasedAt;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static long calculatePlatformFee(long grossAmount) {
        return (grossAmount * PLATFORM_FEE_PERCENT) / 100L;
    }

    public static SellerSettlement createWithDefaultFee(UUID shopId, UUID orderId, long grossAmount) {
        return create(shopId, orderId, grossAmount, calculatePlatformFee(grossAmount));
    }

    public static SellerSettlement create(UUID shopId, UUID orderId, long grossAmount, long platformFee) {
        var now = Instant.now();
        return new SellerSettlement(
            UUID.randomUUID(),
            shopId,
            orderId,
            grossAmount,
            platformFee,
            grossAmount - platformFee,
            SettlementStatus.HOLDING,
            now.plus(3, ChronoUnit.DAYS),
            null,
            now,
            true);
    }

    public void release() {
        this.status = SettlementStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public void withdraw() {
        this.status = SettlementStatus.WITHDRAWN;
    }

    public void cancel() {
        this.status = SettlementStatus.CANCELLED;
    }

    public void adjustAndRelease(long newGrossAmount) {
        this.grossAmount = newGrossAmount;
        this.platformFee = calculatePlatformFee(newGrossAmount);
        this.netAmount = newGrossAmount - this.platformFee;
        this.status = SettlementStatus.RELEASED;
        this.releasedAt = Instant.now();
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
