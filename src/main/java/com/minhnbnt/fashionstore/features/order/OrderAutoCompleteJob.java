package com.minhnbnt.fashionstore.features.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCompleteJob {

    private final OrderService orderService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready: scanning for delivered orders ready to auto-complete");
        autoCompleteDeliveredOrders();
    }

    @Scheduled(fixedDelayString = "${fashionstore.order.auto-complete-interval-ms:3600000}", initialDelay = 15000)
    public void autoCompleteDeliveredOrders() {
        var ready = orderService.findDeliveredReadyForAutoComplete();
        if (ready.isEmpty()) {
            return;
        }
        log.info("Running order auto-complete for {} delivered orders", ready.size());
        for (var order : ready) {
            try {
                // Gọi qua proxy của Spring để @Transactional trên service tách biệt từng đơn.
                orderService.systemAutoCompleteOrder(order.getId());
                log.info("Successfully auto-completed delivered order {} ({})", order.getCode(), order.getId());
            } catch (Exception ex) {
                log.error("Failed to auto-complete order {}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }
}

