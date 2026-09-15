package com.brogrammers.open_mic_hub_service.discovery.service;

import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryActor;

import java.util.List;
import java.util.UUID;

/**
 * Records what a person did while looking for an artist, and what they were shown, so ranking can
 * take both into account.
 *
 * <p>The recording methods are best-effort and return nothing: discovery worked, the response is
 * already being written, and failing to log the fact must never turn a good search into an error.
 * The actor is an account or a browser's visitor id; a request with neither is recorded nowhere.
 */
public interface InteractionService {

    /** A search with words in it. */
    void recordSearch(DiscoveryActor actor, String query, String genre, String city,
                      String occasion, Double budgetPerHour);

    /** Browse with filters and no text. Ignored when no filter was set — that says nothing. */
    void recordBrowse(DiscoveryActor actor, String genre, String city, String occasion,
                      Double budgetPerHour);

    /** An artist's profile was opened. De-duplicated within a short window. */
    void recordProfileView(DiscoveryActor actor, Long artistId);

    /** A ranked list was served, in the order it was shown. */
    void recordImpressions(DiscoveryActor actor, UUID requestId, String surface, String query,
                           List<Long> artistIds, String strategy);

    /**
     * An artist was chosen from a served list.
     *
     * <p>Credited only when that list was served to this same actor and contained that artist, so a
     * click cannot be posted against someone else's list or for an artist nobody was shown.
     *
     * @return whether it was recorded
     */
    boolean recordClick(DiscoveryActor actor, UUID requestId, Long artistId, Integer position);

    /**
     * Moves a browser's history onto the account that has just signed in on it.
     *
     * <p>Synchronous, unlike the rest: the client calls it once, right after signing in, and the
     * first personalised page should already reflect what was done before.
     *
     * @return how many rows moved
     */
    int claimVisitorHistory(String visitorId, Long userId);
}
