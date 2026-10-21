package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.settings.ConfigAuditResponse;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Ghi và đọc lịch sử thay đổi cấu hình; ghi chung transaction với thay đổi gốc.
 */
@Service
@RequiredArgsConstructor
public class ConfigAuditService {

    private final ConfigAuditRepository audits;

    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
        ConfigAuditEntity entity,
        String entityKey,
        String action,
        @Nullable Object oldValue,
        @Nullable Object newValue,
        UUID changedBy
    ) {
        audits.insert(entity, entityKey, action, toJson(oldValue), toJson(newValue), changedBy);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConfigAuditResponse> page(@Nullable ConfigAuditEntity entity, int page, int size) {
        return new PageResponse<>(audits.search(entity, page, size), audits.count(entity), page, size);
    }

    private @Nullable String toJson(@Nullable Object value) {
        if (value == null) {
            return null;
        }

        return objectMapper.writeValueAsString(value);
    }
}
