package com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.entity.ArtistUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ArtistUnavailabilityRepository extends JpaRepository<ArtistUnavailability, Long> {

    /** Whether the given date falls inside any of the artist's blackout ranges, inclusive. */
    @Query("""
            SELECT COUNT(u) > 0 FROM ArtistUnavailability u
            WHERE u.artist = :artist
              AND u.startDate <= :date
              AND u.endDate >= :date
            """)
    boolean existsByArtistAndDate(@Param("artist") Artist artist, @Param("date") LocalDate date);
}
