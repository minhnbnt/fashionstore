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

@Table("product_answer")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ProductAnswer implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID questionId;

    private UUID authorId;

    private String authorDisplayName;

    private ProductAnswerRole authorRole;

    private boolean verifiedBuyer;

    private String content;

    private @Nullable Instant editedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static ProductAnswer createShopAnswer(
        UUID questionId,
        UUID authorId,
        String shopName,
        String content
    ) {
        var now = Instant.now();
        return new ProductAnswer(
            UUID.randomUUID(),
            questionId,
            authorId,
            shopName,
            ProductAnswerRole.SHOP,
            false,
            content,
            null,
            now,
            now,
            true
        );
    }

    public static ProductAnswer createVerifiedBuyerAnswer(
        UUID questionId,
        UUID authorId,
        String buyerDisplayName,
        String content
    ) {
        var now = Instant.now();
        return new ProductAnswer(
            UUID.randomUUID(),
            questionId,
            authorId,
            buyerDisplayName,
            ProductAnswerRole.VERIFIED_BUYER,
            true,
            content,
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

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
