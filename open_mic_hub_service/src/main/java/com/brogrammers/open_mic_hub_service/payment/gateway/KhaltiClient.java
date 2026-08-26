package com.brogrammers.open_mic_hub_service.payment.gateway;

import com.brogrammers.open_mic_hub_service.booking.dto.response.KhaltiInitiateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KhaltiClient {

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

    public Mono<KhaltiInitiateResponse> initiate(Long purchaseOrderId,
                                                 String purchaseOrderName,
                                                 BigDecimal amount,
                                                 String returnUrl,
                                                 String websiteUrl) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new IllegalArgumentException("Amount must be greater than zero."));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("return_url", returnUrl);
        body.put("website_url", websiteUrl);
        body.put("amount", toPaisa(amount));
        body.put("purchase_order_id", purchaseOrderId);
        body.put("purchase_order_name", purchaseOrderName);

        log.info("[Khalti] Initiating payment for order {} ({} paisa)", purchaseOrderId, toPaisa(amount));

        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/epayment/initiate/")
                .header(HttpHeaders.AUTHORIZATION, "Key " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KhaltiInitiateResponse.class)
                .doOnError(e -> log.error("[Khalti] Initiate failed: {}", e.getMessage()));
    }

    /**
     * Asks Khalti what actually happened to a payment. This is a server-to-server call and its
     * answer is the only thing that may be used to settle a payment.
     */
    public KhaltiLookupResponse lookup(String pidx) {
        log.info("[Khalti] Looking up pidx {}", pidx);
        try {
            return webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/epayment/lookup/")
                    .header(HttpHeaders.AUTHORIZATION, "Key " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pidx", pidx))
                    .retrieve()
                    .bodyToMono(KhaltiLookupResponse.class)
                    .block(Duration.ofSeconds(20));
        } catch (Exception e) {
            // Fail closed: an unverifiable payment is not a settled payment.
            log.error("[Khalti] Lookup failed for pidx {}: {}", pidx, e.getMessage());
            throw new PaymentVerificationException(
                    "Could not verify this payment with the gateway. It has not been settled.", e);
        }
    }
}
