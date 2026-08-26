package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VirtualCoinRequest {
    private Long artistId;
    private Double balance;
}
