package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Media của một review (bảng con {@code review_media}, design D4): mỗi ảnh/video
 * là 1 row tham chiếu {@code upload} qua FK; {@code kind} là IMAGE hoặc VIDEO,
 * {@code sort_order} giữ thứ tự hiển thị (ảnh theo vị trí trong danh sách, video xếp sau).
 */
@Table("review_media")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ReviewMedia implements Persistable<UUID> {

    public static final String KIND_IMAGE = "IMAGE";

    public static final String KIND_VIDEO = "VIDEO";

    @Id
    private UUID id;

    private UUID reviewId;

    private UUID uploadId;

    private String kind;

    private int sortOrder;

    @Transient
    private boolean newRecord;

    public static ReviewMedia create(UUID reviewId, UUID uploadId, String kind, int sortOrder) {
        return new ReviewMedia(UUID.randomUUID(), reviewId, uploadId, kind, sortOrder, true);
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
