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

/**
 * Báo cáo sản phẩm nghi vi phạm. Đóng hàng loạt theo sản phẩm bằng SQL khi admin gỡ/bác bỏ,
 * nên entity chỉ dùng để tạo mới.
 */
@Table("product_report")
@Getter
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

    @PersistenceCreator
    public ProductReport(
        UUID id,
        UUID productId,
        ReportSource source,
        @Nullable UUID reporterId,
        ProductViolationType reason,
        String description,
        ReportStatus status,
        @Nullable UUID resolvedBy,
        @Nullable Instant resolvedAt,
        @Nullable String resolutionNote,
        Instant createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.source = source;
        this.reporterId = reporterId;
        this.reason = reason;
        this.description = description;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolutionNote = resolutionNote;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static ProductReport byUser(
        UUID productId,
        UUID reporterId,
        ProductViolationType reason,
        String description
    ) {
        var report = new ProductReport(
            UUID.randomUUID(), productId, ReportSource.USER, reporterId, reason, description,
            ReportStatus.OPEN, null, null, null, Instant.now()
        );
        report.newRecord = true;
        return report;
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
