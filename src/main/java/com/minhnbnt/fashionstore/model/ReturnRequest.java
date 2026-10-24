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

@Table("return_request")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ReturnRequest implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID orderId;

    private UUID shopId;

    private UUID buyerId;

    private String reason;

    private @Nullable String description;

    private String[] evidenceKeys;

    private ReturnStatus status;

    private @Nullable String resolution;

    private @Nullable Long refundAmount;

    private @Nullable String sellerNote;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static ReturnRequest create(
        UUID orderId, UUID shopId, UUID buyerId, String reason, @Nullable String description, String[] evidenceKeys) {
        var now = Instant.now();
        return new ReturnRequest(
            UUID.randomUUID(),
            orderId,
            shopId,
            buyerId,
            reason,
            description,
            evidenceKeys,
            ReturnStatus.REQUESTED,
            null,
            null,
            null,
            now,
            now,
            true);
    }

    public void approve(String resolution, @Nullable Long refundAmount, @Nullable String sellerNote) {
        if (status != ReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only requested returns can be approved");
        }
        this.status = ReturnStatus.APPROVED;
        this.resolution = resolution;
        this.refundAmount = refundAmount;
        this.sellerNote = sellerNote;
        this.updatedAt = Instant.now();
    }

    public void reject(String sellerNote) {
        if (status != ReturnStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only requested returns can be rejected");
        }
        this.status = ReturnStatus.REJECTED;
        this.sellerNote = sellerNote;
        this.updatedAt = Instant.now();
    }

    public void complete(@Nullable String sellerNote) {
        if (status != ReturnStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved returns can be completed");
        }
        this.status = ReturnStatus.COMPLETED;
        if (sellerNote != null) {
            this.sellerNote = sellerNote;
        }
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
