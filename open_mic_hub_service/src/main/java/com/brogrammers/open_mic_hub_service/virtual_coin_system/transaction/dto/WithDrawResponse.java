package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WithDrawResponse {
    private Long transactionId;
    private String artistName;
    private Double amount;
    private String transactionType;
    private String transactionPurpose;
    private String transactionStatus;
}
