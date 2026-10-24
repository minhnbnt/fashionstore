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

@Table("shop_decoration")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    public static ShopDecoration createDefault(UUID shopId) {
        var now = Instant.now();
        return new ShopDecoration(UUID.randomUUID(), shopId, "[]", null, null, now, true);
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
