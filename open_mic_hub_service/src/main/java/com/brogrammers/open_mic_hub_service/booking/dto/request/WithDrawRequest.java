package com.brogrammers.open_mic_hub_service.booking.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WithDrawRequest {
    private Long transactionId;
}
