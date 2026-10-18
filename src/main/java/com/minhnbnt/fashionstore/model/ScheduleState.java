package com.minhnbnt.fashionstore.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Trạng thái lịch chạy của banner/popup tại một thời điểm, tính ra chứ không lưu.
 */
public enum ScheduleState {
    INACTIVE,
    SCHEDULED,
    RUNNING,
    EXPIRED;

    public static ScheduleState of(
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        Instant now
    ) {
        if (!active) {
            return INACTIVE;
        }
        if (startsAt != null && startsAt.isAfter(now)) {
            return SCHEDULED;
        }
        if (endsAt != null && !endsAt.isAfter(now)) {
            return EXPIRED;
        }
        return RUNNING;
    }
}
