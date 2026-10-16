package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("seller_withdrawal")
@Getter
public class SellerWithdrawal implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private long amount;

    private String bankName;

    private String bankAccount;

    private String bankHolder;

    private WithdrawalStatus status;

    private Instant createdAt;

    private @Nullable Instant processedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public SellerWithdrawal(
        UUID id,
        UUID shopId,
        long amount,
        String bankName,
        String bankAccount,
        String bankHolder,
        WithdrawalStatus status,
        Instant createdAt,
        @Nullable Instant processedAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.amount = amount;
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.bankHolder = bankHolder;
        this.status = status;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
        this.newRecord = false;
    }

    public static SellerWithdrawal request(
        UUID shopId,
        long amount,
        String bankName,
        String bankAccount,
        String bankHolder
    ) {
        var withdrawal = new SellerWithdrawal(
            UUID.randomUUID(),
            shopId,
            amount,
            bankName,
            bankAccount,
            bankHolder,
            WithdrawalStatus.REQUESTED,
            Instant.now(),
            null
        );
        withdrawal.newRecord = true;
        return withdrawal;
    }

    public void complete() {
        this.status = WithdrawalStatus.COMPLETED;
        this.processedAt = Instant.now();
    }

    public void reject() {
        this.status = WithdrawalStatus.REJECTED;
        this.processedAt = Instant.now();
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
