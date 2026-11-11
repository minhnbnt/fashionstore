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

@Table("ai_message")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class AiMessage implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID conversationId;

    private String role;

    private String content;

    private @Nullable String metadata;

    private Instant sentAt;

    @Transient
    private boolean newRecord;

    public static AiMessage of(UUID conversationId, String role, String content, @Nullable String metadata) {
        return new AiMessage(
            UUID.randomUUID(),
            conversationId,
            role,
            content,
            metadata,
            Instant.now(),
            true
        );
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
