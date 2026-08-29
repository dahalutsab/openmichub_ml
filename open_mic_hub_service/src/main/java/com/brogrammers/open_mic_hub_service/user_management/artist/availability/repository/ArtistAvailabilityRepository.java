package com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistAvailabilityRepository extends JpaRepository<ArtistAvailability, Long> {

    /**
     * The artist's availability for one weekday, with its time slots already loaded.
     *
     * <p>{@code availabilityTimes} is a lazy {@code @OneToMany}, and the booking path reads it
     * after the repository's session has closed. Without the fetch graph every call to
     * {@code bookArtist} died on a {@code LazyInitializationException} — booking an artist was
     * impossible through the API. The seeded bookings were inserted in SQL, so nothing exercised
     * it.
     *
     * <p>Loading the collection here rather than making the caller transactional is deliberate:
     * the booking method goes on to call a payment gateway and send mail, and holding a database
     * transaction open across those is worse than one extra join.
     */
    @EntityGraph(attributePaths = "availabilityTimes")
    Optional<ArtistAvailability> findByArtistAndDayOfWeek(Artist artist, DayOfWeek requestedDay);

    /**
     * Every weekday the artist has published hours for, with those hours loaded.
     *
     * <p>A day row with no times on it is not availability — nothing can be booked against it —
     * so the checklist has to look at the times, not just the day.
     */
    @EntityGraph(attributePaths = "availabilityTimes")
    List<ArtistAvailability> findAllByArtist(Artist artist);
}
