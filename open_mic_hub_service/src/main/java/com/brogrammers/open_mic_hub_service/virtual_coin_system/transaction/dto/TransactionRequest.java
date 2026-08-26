package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRequest {
    private Long artistId;
    private Long bookingId;
    private Double amount;
    private TransactionType transactionType;
    private TransactionPurpose transactionPurpose;
    private Status status;
}
