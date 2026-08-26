package com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.Optional;

@Repository
public interface ArtistAvailabilityRepository extends JpaRepository<ArtistAvailability, Long> {
    Optional<ArtistAvailability> findByArtistAndDayOfWeek(Artist artist, DayOfWeek requestedDay);
}
