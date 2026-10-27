package com.minhnbnt.fashionstore.features.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCancelJob {

    private final OrderService orderService;

    private final Clock clock;

    @Value("${fashionstore.order.pending-timeout-hours:24}")
    private long pendingTimeoutHours;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready: scanning for expired pending orders older than {} hours", pendingTimeoutHours);
        cancelExpiredPendingOrders();
    }

    @Scheduled(fixedDelayString = "${fashionstore.order.auto-cancel-interval-ms:60000}", initialDelay = 5000)
    public void cancelExpiredPendingOrders() {
        var cutoff = clock.instant().minus(pendingTimeoutHours, ChronoUnit.HOURS);
        log.info("Running order auto-cancel job with cutoff {}", cutoff);
        orderService.cancelExpiredPendingOrders(cutoff);
    }
}
