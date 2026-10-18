package com.minhnbnt.fashionstore.model;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Chuẩn hóa field dùng chung cho nội dung sàn (banner, popup, trang tĩnh, FAQ).
 */
final class ContentFields {

    private ContentFields() {
    }

    /**
     * Chuỗi tùy chọn: trim, rỗng thì coi như không nhập.
     */
    static @Nullable String optional(@Nullable String value) {
        if (value == null) {
            return null;
        }

        var trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    static void requireValidWindow(@Nullable Instant startsAt, @Nullable Instant endsAt) {
        if (startsAt == null || endsAt == null) {
            return;
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
    }
}
