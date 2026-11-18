package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
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

@Table("product_question")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ProductQuestion implements Persistable<UUID> {

    @Id
    private UUID id;

    private String code;

    private UUID productId;

    private UUID shopId;

    private UUID askerId;

    private String askerDisplayName;

    private String content;

    private ProductQuestionStatus status;

    private @Nullable Instant closedAt;

    private @Nullable UUID closedBy;

    private @Nullable Instant editedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static ProductQuestion create(
        String code,
        UUID productId,
        UUID shopId,
        UUID askerId,
        String askerDisplayName,
        String content
    ) {
        var now = Instant.now();
        return new ProductQuestion(
            UUID.randomUUID(),
            code,
            productId,
            shopId,
            askerId,
            askerDisplayName,
            content,
            ProductQuestionStatus.UNANSWERED,
            null,
            null,
            null,
            now,
            now,
            true
        );
    }

    public void editContent(String content) {
        var now = Instant.now();
        this.content = content;
        this.editedAt = now;
        this.updatedAt = now;
    }

    public void markAnswered() {
        if (this.status != ProductQuestionStatus.CLOSED) {
            this.status = ProductQuestionStatus.ANSWERED;
            this.updatedAt = Instant.now();
        }
    }

    public void markUnanswered() {
        if (this.status != ProductQuestionStatus.CLOSED) {
            this.status = ProductQuestionStatus.UNANSWERED;
            this.updatedAt = Instant.now();
        }
    }

    public void close(UUID closedBy) {
        var now = Instant.now();
        this.status = ProductQuestionStatus.CLOSED;
        this.closedAt = now;
        this.closedBy = closedBy;
        this.updatedAt = now;
    }

    public void reopen(boolean hasShopAnswer) {
        var now = Instant.now();
        this.closedAt = null;
        this.closedBy = null;
        if (hasShopAnswer) {
            this.status = ProductQuestionStatus.ANSWERED;
        } else {
            this.status = ProductQuestionStatus.UNANSWERED;
        }
        this.updatedAt = now;
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
