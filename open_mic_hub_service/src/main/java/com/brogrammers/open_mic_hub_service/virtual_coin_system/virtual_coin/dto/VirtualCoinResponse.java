package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VirtualCoinResponse {
    private Long virtualCoinId;
    private Double balance;
    private ArtistResponse artist;

    public VirtualCoinResponse(VirtualCoin virtualCoin) {
        this.virtualCoinId = virtualCoin.getVirtualCoinId();
        this.balance = virtualCoin.getBalance();
        if (virtualCoin.getArtist() != null) {
            this.artist = new ArtistResponse(virtualCoin.getArtist());
        }
    }
}
