package com.brogrammers.open_mic_hub_service.payment.gateway;

/**
 * Raised when the payment gateway refuses or cannot serve a request.
 *
 * <p>Exists so the gateway's own HTTP status never becomes ours. Khalti answers an unconfigured or
 * rejected secret key with 401; letting that propagate made the API reply 401 to a perfectly valid
 * session, and the browser's interceptor read that as an expired token and signed the user out
 * mid-checkout. A gateway problem is the gateway's, so it surfaces as 502.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
