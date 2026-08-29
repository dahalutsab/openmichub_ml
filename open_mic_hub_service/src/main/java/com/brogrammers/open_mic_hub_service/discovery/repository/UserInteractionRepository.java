package com.brogrammers.open_mic_hub_service.discovery.repository;

import com.brogrammers.open_mic_hub_service.discovery.entity.InteractionKind;
import com.brogrammers.open_mic_hub_service.discovery.entity.UserInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * The write side of the interaction log.
 *
 * <p>Nothing reads it back here. Personalisation is done by the ML service, which queries this
 * table directly alongside the bookings and the embeddings it already reads — see
 * {@code ml_service/app/personalization.py}.
 */
@Repository
public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {

    /**
     * Whether this person already has this artist logged for this kind since {@code since}.
     *
     * <p>A profile is one page that fetches several things and is often reopened while comparing
     * acts, so without this a single afternoon of browsing writes the same artist twenty times and
     * that artist's weight in the taste profile becomes a measure of how often the page reloaded.
     */
    boolean existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
            Long userId, Long artistId, InteractionKind kind, LocalDateTime since);
}
