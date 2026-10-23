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

@Table("chat_conversation")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static ChatConversation create(UUID shopId, UUID buyerId) {

        var now = Instant.now();

        return new ChatConversation(
            UUID.randomUUID(),
            shopId, buyerId,
            null, now,
            0, 0,
            now, true
        );
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
