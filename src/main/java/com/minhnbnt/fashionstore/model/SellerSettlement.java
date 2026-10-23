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

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
