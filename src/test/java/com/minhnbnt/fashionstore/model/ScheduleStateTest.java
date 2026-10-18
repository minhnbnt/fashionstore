package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleStateTest {

    private static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");

    private static final Duration DAY = Duration.ofDays(1);

    @Test
    void inactiveWinsOverWindow() {
        assertThat(ScheduleState.of(false, null, null, NOW)).isEqualTo(ScheduleState.INACTIVE);
    }

    @Test
    void openWindowIsRunning() {
        assertThat(ScheduleState.of(true, null, null, NOW)).isEqualTo(ScheduleState.RUNNING);
    }

    @Test
    void futureStartIsScheduled() {
        assertThat(ScheduleState.of(true, NOW.plus(DAY), null, NOW)).isEqualTo(ScheduleState.SCHEDULED);
    }

    @Test
    void startAtNowIsRunning() {
        assertThat(ScheduleState.of(true, NOW, NOW.plus(DAY), NOW)).isEqualTo(ScheduleState.RUNNING);
    }

    @Test
    void endAtNowIsExpired() {
        assertThat(ScheduleState.of(true, NOW.minus(DAY), NOW, NOW)).isEqualTo(ScheduleState.EXPIRED);
    }
}
