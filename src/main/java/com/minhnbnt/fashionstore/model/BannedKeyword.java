package com.minhnbnt.fashionstore.model;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Từ khóa cấm: tên/mô tả sản phẩm chứa từ này thì hệ thống gắn cờ chờ admin xem.
 */
@Table("banned_keyword")
@Getter
public class BannedKeyword implements Persistable<UUID> {

    @Id
    private UUID id;

    private String keyword;

    private ProductViolationType violationType;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public BannedKeyword(
        UUID id,
        String keyword,
        ProductViolationType violationType,
        @Nullable UUID createdBy,
        Instant createdAt
    ) {
        this.id = id;
        this.keyword = keyword;
        this.violationType = violationType;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    /**
     * Lưu dạng đã cắt khoảng trắng + chữ thường, để unique không phân biệt hoa thường.
     */
    public static BannedKeyword create(String keyword, ProductViolationType violationType, UUID createdBy) {
        var normalized = keyword.strip().toLowerCase(Locale.ROOT);
        var bannedKeyword = new BannedKeyword(
            UUID.randomUUID(), normalized, violationType, createdBy, Instant.now()
        );
        bannedKeyword.newRecord = true;
        return bannedKeyword;
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
