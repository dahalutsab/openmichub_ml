package com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto;

import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ArtistResponse {
    private Long artistId;
    private String artistStageName;
    private String email;
    private Double virtualCoinBalance;

    public ArtistResponse(VirtualCoin virtualCoin){
        this.artistId = virtualCoin.getArtist().getId();
        this.artistStageName = virtualCoin.getArtist().getStageName();
        this.email = virtualCoin.getArtist().getUser().getEmailId();
        this.virtualCoinBalance = virtualCoin.getBalance();
    }
}
