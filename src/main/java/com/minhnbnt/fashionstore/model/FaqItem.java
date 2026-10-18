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

/**
 * Một cặp hỏi–đáp trên trang FAQ. Câu trả lời là Markdown.
 */
@Table("faq_item")
@Getter
public class FaqItem implements Persistable<UUID> {

    @Id
    private UUID id;

    private FaqCategory category;

    private String question;

    private String answer;

    private int sortOrder;

    private boolean published;

    private @Nullable UUID updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public FaqItem(
        UUID id,
        FaqCategory category,
        String question,
        String answer,
        int sortOrder,
        boolean published,
        @Nullable UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.published = published;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static FaqItem create(
        FaqCategory category,
        String question,
        String answer,
        int sortOrder,
        boolean published,
        UUID adminId
    ) {
        var now = Instant.now();
        var item = new FaqItem(UUID.randomUUID(), category, question, answer, 0, false, adminId, now, now);
        item.update(category, question, answer, sortOrder, published, adminId);
        item.newRecord = true;
        return item;
    }

    public void update(
        FaqCategory category,
        String question,
        String answer,
        int sortOrder,
        boolean published,
        UUID adminId
    ) {
        this.category = category;
        this.question = question.trim();
        this.answer = answer.trim();
        this.sortOrder = sortOrder;
        this.published = published;
        this.updatedBy = adminId;
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
