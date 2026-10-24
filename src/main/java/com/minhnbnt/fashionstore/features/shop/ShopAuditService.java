package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ghi nhật ký hoạt động của shop. Lỗi ghi audit chỉ được log, không làm hỏng thao tác nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopAuditService {

    private final ShopMemberAuditRepository audits;

    public void record(
        UUID shopId,
        UUID actorId,
        String action,
        @Nullable String targetType,
        @Nullable UUID targetId,
        @Nullable String detail
    ) {
        try {
            audits.save(ShopMemberAudit.record(shopId, actorId, action, targetType, targetId, detail));
        } catch (RuntimeException e) {
            // ponytail: lỗi audit bị nuốt để không rollback nghiệp vụ; Postgres vẫn có thể abort transaction.
            log.error("Failed to record shop audit action {} for shop {}", action, shopId, e);
        }
    }
}
