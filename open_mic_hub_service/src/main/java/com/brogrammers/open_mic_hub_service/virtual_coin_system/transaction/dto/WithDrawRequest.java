package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WithDrawRequest {
    private double amount;
}
