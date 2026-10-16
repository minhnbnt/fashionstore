package com.minhnbnt.fashionstore.model;

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
public class SellerSettlement implements Persistable<UUID> {

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

    @PersistenceCreator
    public SellerSettlement(
        UUID id,
        UUID shopId,
        UUID orderId,
        long grossAmount,
        long platformFee,
        long netAmount,
        SettlementStatus status,
        Instant holdUntil,
        @Nullable Instant releasedAt,
        Instant createdAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.orderId = orderId;
        this.grossAmount = grossAmount;
        this.platformFee = platformFee;
        this.netAmount = netAmount;
        this.status = status;
        this.holdUntil = holdUntil;
        this.releasedAt = releasedAt;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static SellerSettlement create(UUID shopId, UUID orderId, long grossAmount, long platformFee) {
        var now = Instant.now();
        var settlement = new SellerSettlement(
            UUID.randomUUID(),
            shopId,
            orderId,
            grossAmount,
            platformFee,
            grossAmount - platformFee,
            SettlementStatus.HOLDING,
            now.plus(3, ChronoUnit.DAYS),
            null,
            now
        );
        settlement.newRecord = true;
        return settlement;
    }

    public void release() {
        this.status = SettlementStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public void withdraw() {
        this.status = SettlementStatus.WITHDRAWN;
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
