package com.brogrammers.open_mic_hub_service.payment.gateway;

/**
 * Raised when the payment gateway cannot confirm what happened to a payment.
 *
 * <p>Treated as a failure to settle rather than a failure to pay: the payment is left untouched so
 * the callback can be retried once the gateway is reachable again.
 */
public class PaymentVerificationException extends RuntimeException {

    public PaymentVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
