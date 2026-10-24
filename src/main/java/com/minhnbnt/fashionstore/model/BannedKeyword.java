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
import java.util.Locale;
import java.util.UUID;

/**
 * Từ khóa cấm: tên/mô tả sản phẩm chứa từ này thì hệ thống gắn cờ chờ admin xem.
 */
@Table("banned_keyword")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class BannedKeyword implements Persistable<UUID> {

    @Id
    private UUID id;

    private String keyword;

    private ProductViolationType violationType;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    /**
     * Lưu dạng đã cắt khoảng trắng + chữ thường, để unique không phân biệt hoa thường.
     */
    public static BannedKeyword create(String keyword, ProductViolationType violationType, UUID createdBy) {
        var normalized = keyword.strip().toLowerCase(Locale.ROOT);
        return new BannedKeyword(UUID.randomUUID(), normalized, violationType, createdBy, Instant.now(), true);
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
