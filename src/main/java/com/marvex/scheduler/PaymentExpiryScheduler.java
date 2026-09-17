package com.marvex.scheduler;

import com.marvex.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60_000)
    public void expirePayments() {
        paymentService.expireStalePayments();
    }
}
