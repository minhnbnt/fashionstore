package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("ai_conversation")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class AiConversation implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private String title;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static AiConversation of(UUID userId, String title) {
        Instant now = Instant.now();
        return new AiConversation(
            UUID.randomUUID(),
            userId,
            title,
            now,
            now,
            true
        );
    }

    public void updateTitle(String newTitle) {
        this.title = newTitle;
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
