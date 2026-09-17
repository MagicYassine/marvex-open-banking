package com.marvex.provider;

public interface PaymentProvider {
    ProviderPaymentResponse createPayment(long amount, String reference, String institutionId, String payeeName, String sortCode, String accountNumber);

    String submitPayment(String consent, String paymentIdempotencyId, long amount, String reference, String payeeName, String sortCode, String accountNumber);

    String verifyPayment(String providerPaymentId, String consent);
}
