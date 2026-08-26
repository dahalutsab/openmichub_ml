package com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VirtualCoinRepository extends JpaRepository<VirtualCoin, Long> {
    Optional<VirtualCoin> findVirtualCoinByArtist(Artist artistId);
}
