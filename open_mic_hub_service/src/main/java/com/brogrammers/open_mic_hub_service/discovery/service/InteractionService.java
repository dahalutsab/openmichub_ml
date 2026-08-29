package com.brogrammers.open_mic_hub_service.discovery.service;

/**
 * Records what a signed-in person did while looking for an artist, so ranking can take their
 * history into account.
 *
 * <p>Every method is best-effort and returns nothing: discovery worked, the response is already
 * being written, and failing to log the fact must never turn a good search into an error. Nothing
 * is recorded for an anonymous visitor.
 */
public interface InteractionService {

    /** A search with words in it. */
    void recordSearch(Long userId, String query, String genre, String city,
                      String occasion, Double budgetPerHour);

    /** Browse with filters and no text. Ignored when no filter was set — that says nothing. */
    void recordBrowse(Long userId, String genre, String city, String occasion, Double budgetPerHour);

    /** An artist's profile was opened. De-duplicated within a short window. */
    void recordProfileView(Long userId, Long artistId);
}
