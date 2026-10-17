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

@Table("chat_message")
@Getter
public class ChatMessage implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID conversationId;

    private UUID senderId;

    private String senderType;

    private String content;

    private @Nullable String imageKey;

    private Instant sentAt;

    private boolean isRead;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ChatMessage(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderType,
        String content,
        @Nullable String imageKey,
        Instant sentAt,
        boolean isRead
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
        this.imageKey = imageKey;
        this.sentAt = sentAt;
        this.isRead = isRead;
        this.newRecord = false;
    }

    public static ChatMessage of(
        UUID conversationId,
        UUID senderId,
        String senderType,
        String content,
        @Nullable String imageKey
    ) {
        var msg = new ChatMessage(
            UUID.randomUUID(),
            conversationId,
            senderId,
            senderType,
            content,
            imageKey,
            Instant.now(),
            false
        );
        msg.newRecord = true;
        return msg;
    }

    public void markRead() {
        this.isRead = true;
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
