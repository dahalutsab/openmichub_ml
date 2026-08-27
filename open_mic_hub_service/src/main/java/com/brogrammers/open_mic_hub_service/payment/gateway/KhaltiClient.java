package com.brogrammers.open_mic_hub_service.payment.gateway;

import com.brogrammers.open_mic_hub_service.booking.dto.response.KhaltiInitiateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Single point of contact with the Khalti payment gateway.
 *
 * <p>Previously the initiate call was duplicated in two services, each with the secret key inlined
 * as a literal. Both now delegate here, and the key is injected from configuration.
 *
 * <p>Both calls block and both translate every failure into {@link PaymentGatewayException}. That
 * is deliberate on two counts. Returning a {@code Mono} to a Spring MVC controller meant the call
 * completed on a Reactor thread, where the security context no longer exists — when it failed,
 * Spring Security's entry point ran on the async dispatch and answered 401 with an empty body,
 * which is neither true nor diagnosable. And letting {@link WebClientResponseException} escape put
 * Khalti's status on our response, so an unconfigured key logged the user out.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KhaltiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final WebClient.Builder webClientBuilder;

    @Value("${khalti.secret-key}")
    private String secretKey;

    @Value("${khalti.base-url}")
    private String baseUrl;

    /** Converts a rupee amount to the paisa integer Khalti expects, without float drift. */
    public static long toPaisa(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    /**
     * Fails early when there is no key to authenticate with.
     *
     * <p>Without this the request still goes out, as {@code Authorization: Key } with nothing after
     * it, and comes back 401 — indistinguishable from a revoked key. Saying so plainly is the
     * difference between a five-minute fix and an afternoon.
     */
    private void requireConfiguredKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentGatewayException(
                    "Online payment is not available: the Khalti secret key is not configured on the server. "
                            + "Set KHALTI_SECRET_KEY and restart.");
        }
    }

    public KhaltiInitiateResponse initiate(Long purchaseOrderId,
                                          String purchaseOrderName,
                                          BigDecimal amount,
                                          String returnUrl,
                                          String websiteUrl) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        requireConfiguredKey();

        Map<String, Object> body = new HashMap<>();
        body.put("return_url", returnUrl);
        body.put("website_url", websiteUrl);
        body.put("amount", toPaisa(amount));
        body.put("purchase_order_id", purchaseOrderId);
        body.put("purchase_order_name", purchaseOrderName);

        log.info("[Khalti] Initiating payment for order {} ({} paisa)", purchaseOrderId, toPaisa(amount));

        KhaltiInitiateResponse response;
        try {
            response = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/epayment/initiate/")
                    .header(HttpHeaders.AUTHORIZATION, "Key " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(KhaltiInitiateResponse.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            // The gateway's own body usually names the offending field; it is the most useful
            // thing in the log and never reaches the browser.
            log.error("[Khalti] Initiate failed for order {}: {} — {}",
                    purchaseOrderId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException(describe(e), e);
        } catch (Exception e) {
            log.error("[Khalti] Initiate failed for order {}: {}", purchaseOrderId, e.getMessage());
            throw new PaymentGatewayException(
                    "Could not reach the payment gateway. Please try again in a moment.", e);
        }

        if (response == null || response.getPaymentUrl() == null || response.getPaymentUrl().isBlank()) {
            throw new PaymentGatewayException("The payment gateway did not return a payment link.");
        }
        return response;
    }

    /**
     * Asks Khalti what actually happened to a payment. This is a server-to-server call and its
     * answer is the only thing that may be used to settle a payment.
     */
    public KhaltiLookupResponse lookup(String pidx) {
        log.info("[Khalti] Looking up pidx {}", pidx);
        requireConfiguredKey();
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/epayment/lookup/")
                    .header(HttpHeaders.AUTHORIZATION, "Key " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pidx", pidx))
                    .retrieve()
                    .bodyToMono(KhaltiLookupResponse.class)
                    .block(TIMEOUT);
        } catch (Exception e) {
            // Fail closed: an unverifiable payment is not a settled payment.
            log.error("[Khalti] Lookup failed for pidx {}: {}", pidx, e.getMessage());
            throw new PaymentVerificationException(
                    "Could not verify this payment with the gateway. It has not been settled.", e);
        }
    }

    /**
     * A message for the booker.
     *
     * <p>Deliberately vague about our own misconfiguration — a 401 from Khalti means our key is
     * wrong, which is not the booker's problem to read about, and the detail is already in the log.
     */
    private String describe(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return "Online payment is temporarily unavailable. Please try again later.";
        }
        if (status == 400) {
            return "The payment gateway rejected this request. Please check the booking and try again.";
        }
        return "The payment gateway is unavailable right now. Please try again in a moment.";
    }
}
