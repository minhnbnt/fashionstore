package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.features.payment.PaymentFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryJob {

    private final PaymentFlowService paymentFlow;

    @Scheduled(fixedDelay = 60_000)
    public void expireOverduePayments() {
        var expiredCount = paymentFlow.expireOverdueOrders();
        if (expiredCount > 0) {
            log.info("Auto-expired {} overdue awaiting-payment orders", expiredCount);
        }
    }
}
