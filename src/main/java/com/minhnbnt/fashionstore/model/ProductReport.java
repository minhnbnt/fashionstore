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

/**
 * Báo cáo sản phẩm nghi vi phạm. Đóng hàng loạt theo sản phẩm bằng SQL khi admin gỡ/bác bỏ,
 * nên entity chỉ dùng để tạo mới.
 */
@Table("product_report")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ProductReport implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID productId;

    private ReportSource source;

    private @Nullable UUID reporterId;

    private ProductViolationType reason;

    private String description;

    private ReportStatus status;

    private @Nullable UUID resolvedBy;

    private @Nullable Instant resolvedAt;

    private @Nullable String resolutionNote;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static ProductReport byUser(
        UUID productId, UUID reporterId, ProductViolationType reason, String description) {
        return new ProductReport(
            UUID.randomUUID(),
            productId,
            ReportSource.USER,
            reporterId,
            reason,
            description,
            ReportStatus.OPEN,
            null,
            null,
            null,
            Instant.now(),
            true);
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
