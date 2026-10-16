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

@Table("shop_decoration")
@Getter
public class ShopDecoration implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID shopId;

    private String layout;

    private @Nullable String draft;

    private @Nullable Instant publishedAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public ShopDecoration(
        UUID id,
        UUID shopId,
        String layout,
        @Nullable String draft,
        @Nullable Instant publishedAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.shopId = shopId;
        this.layout = layout;
        this.draft = draft;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static ShopDecoration createDefault(UUID shopId) {
        var now = Instant.now();
        var deco = new ShopDecoration(
            UUID.randomUUID(),
            shopId,
            "[]",
            null,
            null,
            now
        );
        deco.newRecord = true;
        return deco;
    }

    public void saveDraft(String draftJson) {
        this.draft = draftJson;
        this.updatedAt = Instant.now();
    }

    public void publish() {
        if (this.draft != null) {
            this.layout = this.draft;
            this.draft = null;
            this.publishedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    public void discardDraft() {
        this.draft = null;
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
