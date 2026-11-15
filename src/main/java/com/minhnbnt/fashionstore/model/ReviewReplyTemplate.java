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

@Table("review_reply_template")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ReviewReplyTemplate implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private String title;

    private String content;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static ReviewReplyTemplate create(
        UUID shopId,
        UUID createdBy,
        String title,
        String content
    ) {
        var now = Instant.now();
        return new ReviewReplyTemplate(
            UUID.randomUUID(),
            shopId,
            title,
            content,
            createdBy,
            now,
            now,
            true
        );
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
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

