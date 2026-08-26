package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VirtualCoinService {

    /** Returns the artist's wallet, creating an empty one on first use. */
    VirtualCoin getOrCreateWallet(Artist artist);

    VirtualCoinResponse createOrUpdateVirtualCoin(VirtualCoinRequest virtualCoinRequest);
    VirtualCoinResponse getVirtualCoinByArtistId(Long artistId);
    VirtualCoinResponse getVirtualCoinById(Long virtualCoinId);
    Page<VirtualCoinResponse> getAllVirtualCoins(Pageable pageable);
    VirtualCoinResponse getLoggedInArtistVirtualCoin();
}
