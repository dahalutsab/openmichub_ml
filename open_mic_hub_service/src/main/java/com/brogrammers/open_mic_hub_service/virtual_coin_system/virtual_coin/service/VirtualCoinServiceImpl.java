package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinResponse;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class VirtualCoinServiceImpl implements VirtualCoinService {
    private final ArtistRepository artistRepository;
    private final VirtualCoinRepository virtualCoinRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    @Override
    public VirtualCoinResponse createOrUpdateVirtualCoin(VirtualCoinRequest virtualCoinRequest) {
        log.info("Creating or updating virtual coin for artist with ID: {}", virtualCoinRequest.getArtistId());

        // Fetch the artist or throw an exception if not found
        Artist artist = artistRepository.findById(virtualCoinRequest.getArtistId()).orElseThrow(
                () -> new RuntimeException("Artist not found with ID: " + virtualCoinRequest.getArtistId())
        );

        // Check if a virtual coin exists for the artist
        VirtualCoin virtualCoin = virtualCoinRepository.findVirtualCoinByArtist(artist)
                .orElseGet(() -> {
                    log.info("No virtual coin found for artist with ID: {}. Creating a new one.", artist.getId());
                    return new VirtualCoin();
                });

        // Set the balance and associate the artist
        virtualCoin.setBalance(virtualCoinRequest.getBalance());
        virtualCoin.setArtist(artist);

        // Save the virtual coin (create or update)
        virtualCoinRepository.save(virtualCoin);

        // Return the response
        return new VirtualCoinResponse(virtualCoin);
    }

    @Override
    public VirtualCoinResponse getVirtualCoinByArtistId(Long artistId) {

        Artist artist = artistRepository.findById(artistId).orElseThrow(
                () -> new RuntimeException("Artist not found with ID: " + artistId)
        );

        log.info("Fetching virtual coin for artist with ID: {}", artist.getId());
        VirtualCoin virtualCoin = virtualCoinRepository.findVirtualCoinByArtist(artist).orElseThrow(
                () -> new RuntimeException("Virtual coin not found for artist with ID: " + artist.getId())
        );
        return new VirtualCoinResponse(virtualCoin);
    }

    @Override
    public VirtualCoinResponse getVirtualCoinById(Long virtualCoinId) {
        log.info("Fetching virtual coin with ID: {}", virtualCoinId);
        VirtualCoin virtualCoin = virtualCoinRepository.findById(virtualCoinId).orElseThrow(
                () -> new RuntimeException("Virtual coin not found with ID: " + virtualCoinId)
        );
        return new VirtualCoinResponse(virtualCoin);
    }

    @Override
    public Page<VirtualCoinResponse> getAllVirtualCoins(Pageable pageable) {
        log.info("Fetching all virtual coins with pagination: {}", pageable);
        Page<VirtualCoin> virtualCoins = virtualCoinRepository.findAll(pageable);
        return virtualCoins.map(VirtualCoinResponse::new);
    }

    @Override
    public VirtualCoinResponse getLoggedInArtistVirtualCoin() {
        log.info("Fetching virtual coin for logged-in artist");
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        VirtualCoin virtualCoin = virtualCoinRepository.findVirtualCoinByArtist(artist)
                .orElseThrow(() -> new RuntimeException("Virtual coin not found for logged-in artist with ID: " + artist.getId()));
        return new VirtualCoinResponse(virtualCoin);
    }
}
