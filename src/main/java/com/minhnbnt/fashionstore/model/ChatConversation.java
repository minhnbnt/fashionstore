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

@Table("chat_conversation")
@Getter
public class ChatConversation implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private UUID buyerId;

    private @Nullable String lastMessageText;

    private Instant lastMessageAt;

    private int sellerUnreadCount;

    private int buyerUnreadCount;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ChatConversation(
        UUID id,
        UUID shopId,
        UUID buyerId,
        @Nullable String lastMessageText,
        Instant lastMessageAt,
        int sellerUnreadCount,
        int buyerUnreadCount,
        Instant createdAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.buyerId = buyerId;
        this.lastMessageText = lastMessageText;
        this.lastMessageAt = lastMessageAt;
        this.sellerUnreadCount = sellerUnreadCount;
        this.buyerUnreadCount = buyerUnreadCount;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ChatConversation create(UUID shopId, UUID buyerId) {
        var now = Instant.now();
        var conv = new ChatConversation(
            UUID.randomUUID(),
            shopId,
            buyerId,
            null,
            now,
            0,
            0,
            now
        );
        conv.newRecord = true;
        return conv;
    }

    public void recordNewMessage(String text, boolean sentByBuyer) {
        this.lastMessageText = text;
        this.lastMessageAt = Instant.now();
        if (sentByBuyer) {
            this.sellerUnreadCount++;
        } else {
            this.buyerUnreadCount++;
        }
    }

    public void markReadBySeller() {
        this.sellerUnreadCount = 0;
    }

    public void markReadByBuyer() {
        this.buyerUnreadCount = 0;
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
