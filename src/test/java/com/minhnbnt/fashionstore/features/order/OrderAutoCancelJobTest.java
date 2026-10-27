package com.minhnbnt.fashionstore.features.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAutoCancelJobTest {

    @Mock
    private OrderService orderService;

    @Mock
    private Clock clock;

    @InjectMocks
    private OrderAutoCancelJob job;

    @Test
    void cancelExpiredPendingOrdersUsesConfiguredTimeout() {
        var fixedInstant = Instant.parse("2026-10-27T10:00:00Z");
        when(clock.instant()).thenReturn(fixedInstant);
        ReflectionTestUtils.setField(job, "pendingTimeoutHours", 24L);

        job.cancelExpiredPendingOrders();

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(orderService).cancelExpiredPendingOrders(captor.capture());

        var expectedCutoff = fixedInstant.minus(24, ChronoUnit.HOURS);
        assertThat(captor.getValue()).isEqualTo(expectedCutoff);
    }
}
