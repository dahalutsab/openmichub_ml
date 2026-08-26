package com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.entity.ArtistUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistUnavailabilityRepository extends JpaRepository<ArtistUnavailability, Long> {
}
