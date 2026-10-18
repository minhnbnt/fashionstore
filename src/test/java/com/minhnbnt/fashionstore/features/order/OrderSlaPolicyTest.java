package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.config.OrderSlaProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSlaPolicyTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final LocalDate CULTURE_DAY = LocalDate.of(2026, 11, 24);

    private final OrderSlaPolicy policy = new OrderSlaPolicy(new OrderSlaProperties(List.of(CULTURE_DAY)));

    private static Instant vn(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }

    @Test
    void weekdayCountsWholeDays() {
        var now = vn("2026-10-14T10:00");

        assertThat(policy.workingDaysBefore(now, 1)).isEqualTo(vn("2026-10-13T10:00"));
    }

    @Test
    void sundayIsSkipped() {
        var mondayMorning = vn("2026-10-19T10:00");

        // 10 giờ thứ Hai + 14 giờ thứ Bảy, Chủ nhật không tính.
        assertThat(policy.workingDaysBefore(mondayMorning, 1)).isEqualTo(vn("2026-10-17T10:00"));
    }

    @Test
    void clockStopsOnSunday() {
        var sundayMorning = vn("2026-10-18T10:00");
        var sundayNight = vn("2026-10-18T23:00");

        assertThat(policy.workingDaysBefore(sundayMorning, 1)).isEqualTo(vn("2026-10-17T00:00"));
        assertThat(policy.workingDaysBefore(sundayNight, 1)).isEqualTo(vn("2026-10-17T00:00"));
    }

    @Test
    void configuredHolidayIsSkipped() {
        var now = vn("2026-11-25T10:00");

        // Thứ Tư 10h + thứ Hai 24h + thứ Bảy 14h, bỏ thứ Ba 24/11 (lễ) và Chủ nhật.
        assertThat(policy.workingDaysBefore(now, 2)).isEqualTo(vn("2026-11-21T10:00"));
        assertThat(policy.isWorkingDay(CULTURE_DAY)).isFalse();
        assertThat(policy.isWorkingDay(LocalDate.of(2026, 10, 18))).isFalse();
        assertThat(policy.isWorkingDay(LocalDate.of(2026, 10, 17))).isTrue();
    }

    @Test
    void cutoffsUseOneTwoAndSevenWorkingDays() {
        var now = vn("2026-10-14T10:00");

        var cutoffs = policy.cutoffsAt(now);

        assertThat(cutoffs.confirm()).isEqualTo(vn("2026-10-13T10:00"));
        assertThat(cutoffs.handover()).isEqualTo(vn("2026-10-12T10:00"));
        // 7 ngày làm việc lùi qua Chủ nhật 11/10 nên rơi vào thứ Ba 6/10.
        assertThat(cutoffs.delivery()).isEqualTo(vn("2026-10-06T10:00"));
    }
}
