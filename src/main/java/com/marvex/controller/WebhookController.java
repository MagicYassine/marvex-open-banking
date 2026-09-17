package com.marvex.controller;

import com.marvex.dto.WebhookRequest;
import com.marvex.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/payment")
    public void handlePaymentWebhook(@Valid @RequestBody WebhookRequest req) {
        webhookService.process(req);
    }
}
