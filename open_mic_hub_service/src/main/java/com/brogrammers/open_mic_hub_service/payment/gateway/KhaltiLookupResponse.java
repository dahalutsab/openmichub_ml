package com.brogrammers.open_mic_hub_service.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response of Khalti's {@code epayment/lookup/} endpoint — the authoritative record of what a
 * customer actually paid.
 *
 * <p>This is the only trustworthy source of payment status. Parameters arriving on the browser
 * redirect are attacker-controlled and must never be used to settle a payment.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KhaltiLookupResponse {

    private String pidx;

    /** {@code Completed}, {@code Pending}, {@code Initiated}, {@code Refunded}, {@code Expired}, {@code User canceled}. */
    private String status;

    /** Amount actually captured, in paisa. */
    @JsonProperty("total_amount")
    private long totalAmount;

    @JsonProperty("transaction_id")
    private String transactionId;

    private long fee;

    private boolean refunded;

    public boolean isCompleted() {
        return "Completed".equalsIgnoreCase(status) && !refunded;
    }
}
