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
 * File bằng chứng của một yêu cầu đổi/trả (bảng con {@code return_evidence},
 * design D4): mỗi ảnh/video là 1 row tham chiếu {@code upload} qua FK,
 * {@code sort_order} giữ thứ tự hiển thị theo vị trí trong danh sách gốc.
 */
@Table("return_evidence")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ReturnEvidence implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID returnRequestId;

    private UUID uploadId;

    private int sortOrder;

    @Transient
    private boolean newRecord;

    public static ReturnEvidence create(UUID returnRequestId, UUID uploadId, int sortOrder) {
        return new ReturnEvidence(UUID.randomUUID(), returnRequestId, uploadId, sortOrder, true);
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
