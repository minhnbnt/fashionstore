package com.minhnbnt.fashionstore.features.settings;

import com.minhnbnt.fashionstore.dto.settings.PublicSettingsResponse;
import com.minhnbnt.fashionstore.dto.settings.SystemSettingsResponse;
import com.minhnbnt.fashionstore.dto.settings.UpdateSettingsRequest;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cấu hình chung của sàn; cập nhật một phần, mỗi key đổi giá trị đều được ghi lịch sử.
 */
@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private static final String SITE_NAME = "site.name";
    private static final String HOTLINE = "site.hotline";
    private static final String SUPPORT_EMAIL = "site.support_email";
    private static final String FREE_THRESHOLD = "shipping.free_threshold";
    private static final String BASE_ITEMS = "shipping.base_items";
    private static final String HOLD_MINUTES = "order.payment_hold_minutes";
    private static final String AUTO_COMPLETE_DAYS = "order.auto_complete_days";
    private static final String COD_MIN = "order.cod_min_total";
    private static final String COD_MAX = "order.cod_max_total";

    private final SystemSettingRepository settings;

    private final ConfigAuditService audits;

    @Transactional(readOnly = true)
    public SystemSettings current() {
        return parse(settings.findAll());
    }

    @Transactional(readOnly = true)
    public PublicSettingsResponse publicView() {
        var current = current();

        return new PublicSettingsResponse(
            current.siteName(),
            current.hotline(),
            current.supportEmail(),
            current.freeShippingThreshold(),
            current.baseItems()
        );
    }

    @Transactional
    public SystemSettingsResponse update(UUID adminId, UpdateSettingsRequest request) {
        var rows = settings.findAllForUpdate();

        var changes = new LinkedHashMap<String, String>();
        put(changes, SITE_NAME, request.siteName());
        put(changes, HOTLINE, request.hotline());
        put(changes, SUPPORT_EMAIL, request.supportEmail());
        put(changes, FREE_THRESHOLD, request.freeShippingThreshold());
        put(changes, BASE_ITEMS, request.baseItems());
        put(changes, HOLD_MINUTES, request.paymentHoldMinutes());
        put(changes, AUTO_COMPLETE_DAYS, request.autoCompleteDays());
        put(changes, COD_MIN, request.codMinTotal());
        put(changes, COD_MAX, request.codMaxTotal());

        var merged = new LinkedHashMap<>(rows);
        merged.putAll(changes);
        var after = parse(merged);

        if (after.codMaxTotal() != 0 && after.codMaxTotal() < after.codMinTotal()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "COD max total must be 0 or at least COD min total"
            );
        }

        changes.forEach((key, value) -> {
            var old = rows.get(key);
            if (value.equals(old)) {
                return;
            }

            settings.update(key, value, adminId);
            audits.record(ConfigAuditEntity.SETTING, key, "UPDATE", old, value, adminId);
        });

        return toResponse(after);
    }

    public static SystemSettingsResponse toResponse(SystemSettings s) {
        return new SystemSettingsResponse(
            s.siteName(),
            s.hotline(),
            s.supportEmail(),
            s.freeShippingThreshold(),
            s.baseItems(),
            s.paymentHoldMinutes(),
            s.autoCompleteDays(),
            s.codMinTotal(),
            s.codMaxTotal()
        );
    }

    private static void put(Map<String, String> changes, String key, @Nullable Object value) {
        if (value != null) {
            changes.put(key, value.toString());
        }
    }

    private static SystemSettings parse(Map<String, String> rows) {
        return new SystemSettings(
            require(rows, SITE_NAME),
            require(rows, HOTLINE),
            require(rows, SUPPORT_EMAIL),
            Long.parseLong(require(rows, FREE_THRESHOLD)),
            Integer.parseInt(require(rows, BASE_ITEMS)),
            Integer.parseInt(require(rows, HOLD_MINUTES)),
            Integer.parseInt(require(rows, AUTO_COMPLETE_DAYS)),
            Long.parseLong(require(rows, COD_MIN)),
            Long.parseLong(require(rows, COD_MAX))
        );
    }

    private static String require(Map<String, String> rows, String key) {
        var value = rows.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing system setting: " + key);
        }

        return value;
    }
}
