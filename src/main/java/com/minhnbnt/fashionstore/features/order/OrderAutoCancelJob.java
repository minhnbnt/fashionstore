package com.minhnbnt.fashionstore.features.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCancelJob {

    private final OrderService orderService;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready: scanning for expired pending orders");
        cancelExpiredPendingOrders();
    }

    @Scheduled(fixedDelayString = "${fashionstore.order.auto-cancel-interval-ms:60000}", initialDelay = 5000)
    public void cancelExpiredPendingOrders() {
        var expired = orderService.findExpiredPendingOrders();
        if (expired.isEmpty()) {
            return;
        }
        log.info("Running order auto-cancel for {} expired pending orders", expired.size());
        for (var order : expired) {
            try {
                // Gọi qua proxy của Spring để @Transactional trên service có hiệu lực:
                // mỗi đơn hủy trong một transaction riêng, lỗi một đơn không ảnh hưởng đơn khác.
                orderService.systemCancelExpiredOrder(order.getId());
                log.info("Successfully auto-cancelled expired order {} ({})", order.getCode(), order.getId());
            } catch (Exception ex) {
                log.error("Failed to auto-cancel expired order {}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }
}
