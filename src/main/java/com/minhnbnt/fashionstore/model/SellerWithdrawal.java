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
import java.util.UUID;

@Table("seller_withdrawal")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static SellerWithdrawal request(
        UUID shopId, long amount, String bankName, String bankAccount, String bankHolder) {
        return new SellerWithdrawal(
            UUID.randomUUID(),
            shopId,
            amount,
            bankName,
            bankAccount,
            bankHolder,
            WithdrawalStatus.REQUESTED,
            Instant.now(),
            null,
            true);
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
