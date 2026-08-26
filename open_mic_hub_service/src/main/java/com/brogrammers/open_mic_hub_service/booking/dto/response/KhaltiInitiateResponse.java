package com.brogrammers.open_mic_hub_service.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KhaltiInitiateResponse {
    private String pidx;

    @JsonProperty("payment_url")
    private String paymentUrl;

    @JsonProperty("expires_at")
    private String expiresAt;

    @JsonProperty("expires_in")
    private int expiresIn;
}