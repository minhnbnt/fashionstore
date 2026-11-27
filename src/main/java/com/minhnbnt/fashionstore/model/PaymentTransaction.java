package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
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

@Table("payment_transaction")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class PaymentTransaction implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID buyerId;

    private String provider;

    private String txnRef;

    private long amount;

    private long receivedAmount;

    private PaymentTransactionStatus status;

    private String bankBin;

    private String bankCode;

    private String accountNumber;

    private String accountName;

    private String qrImageUrl;

    private @Nullable String bankTransactionId;

    private @Nullable String transferContent;

    private @Nullable Instant paidAt;

    private Instant expiresAt;

    private RefundStatus refundStatus;

    private long refundAmount;

    private @Nullable String refundReason;

    private @Nullable Instant refundedAt;

    private @Nullable UUID refundedBy;

    private @Nullable String refundNote;

    private @Nullable String rawPayload;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static PaymentTransaction create(
        UUID buyerId,
        String txnRef,
        long amount,
        String bankBin,
        String bankCode,
        String accountNumber,
        String accountName,
        String qrImageUrl,
        Instant expiresAt
    ) {
        var now = Instant.now();
        return new PaymentTransaction(
            UUID.randomUUID(),
            buyerId,
            "VIETQR",
            txnRef,
            amount,
            0L,
            PaymentTransactionStatus.PENDING,
            bankBin,
            bankCode,
            accountNumber,
            accountName,
            qrImageUrl,
            null,
            null,
            null,
            expiresAt,
            RefundStatus.NONE,
            0L,
            null,
            null,
            null,
            null,
            null,
            now,
            now,
            true
        );
    }

    public long alreadyRefundedAmount() {
        if (this.refundStatus == RefundStatus.REFUNDED) {
            return this.refundAmount;
        }
        return 0L;
    }

    public void markSuccess(
        long transferAmount,
        @Nullable String bankTransactionId,
        @Nullable String transferContent,
        @Nullable String rawPayload,
        Instant now
    ) {
        this.receivedAmount = this.receivedAmount + transferAmount;
        long effectiveHeld = Math.max(0L, this.receivedAmount - alreadyRefundedAmount());

        this.status = PaymentTransactionStatus.SUCCESS;
        this.bankTransactionId = bankTransactionId;
        this.transferContent = transferContent;
        this.rawPayload = rawPayload;
        this.paidAt = now;
        this.updatedAt = now;

        if (effectiveHeld > this.amount) {
            this.refundStatus = RefundStatus.PENDING;
            this.refundAmount = effectiveHeld - this.amount;
            this.refundReason = "Khách chuyển dư tiền";
        } else if (this.refundStatus == RefundStatus.PENDING) {
            this.refundStatus = RefundStatus.NONE;
            this.refundAmount = 0L;
            this.refundReason = null;
        }
    }

    public void markUnderpaid(
        long transferAmount,
        @Nullable String bankTransactionId,
        @Nullable String transferContent,
        @Nullable String rawPayload,
        Instant now
    ) {
        this.receivedAmount = this.receivedAmount + transferAmount;
        long effectiveHeld = Math.max(0L, this.receivedAmount - alreadyRefundedAmount());

        this.status = PaymentTransactionStatus.UNDERPAID;
        this.bankTransactionId = bankTransactionId;
        this.transferContent = transferContent;
        this.rawPayload = rawPayload;
        this.refundStatus = RefundStatus.PENDING;
        this.refundAmount = effectiveHeld;
        this.refundReason = "Khách chuyển thiếu tiền (" + effectiveHeld + "/" + this.amount + "₫)";
        this.updatedAt = now;
    }

    public void markExpired(Instant now) {
        if (this.status == PaymentTransactionStatus.PENDING) {
            this.status = PaymentTransactionStatus.EXPIRED;
            this.updatedAt = now;
        }
    }

    public void addRefund(long additionalRefundAmount, String reason, Instant now) {
        this.refundStatus = RefundStatus.PENDING;
        this.refundAmount = this.refundAmount + additionalRefundAmount;
        if (this.refundReason == null || this.refundReason.isBlank()) {
            this.refundReason = reason;
        } else if (!this.refundReason.contains(reason)) {
            this.refundReason = this.refundReason + "; " + reason;
        }
        this.updatedAt = now;
    }

    public void markRefunded(UUID adminId, String note, Instant now) {
        if (this.refundStatus != RefundStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is not pending refund");
        }
        this.refundStatus = RefundStatus.REFUNDED;
        this.refundedAt = now;
        this.refundedBy = adminId;
        this.refundNote = note;
        this.updatedAt = now;
    }

    public void markPersisted() {
        this.newRecord = false;
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
