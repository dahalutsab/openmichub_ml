package com.brogrammers.open_mic_hub_service.discovery.entity;

/**
 * What a person did.
 *
 * <p>Kept apart from how much each one counts: the weights live in the ML service, next to the
 * model that uses them, so tuning what a profile view is worth relative to a booking does not mean
 * a database migration.
 */
public enum InteractionKind {

    /** A search with words in it. The query text is the signal. */
    SEARCH,

    /** Browse with filters and no text. A statement of intent all the same. */
    BROWSE,

    /** Opened an artist's profile. The commonest signal, and the weakest one on its own. */
    PROFILE_VIEW,

    /**
     * Chose an artist from a ranked list, carrying the list it came from and the position it held.
     *
     * <p>Usually followed by a PROFILE_VIEW for the same artist, and deliberately not weighted as a
     * second one. What a click adds is the position: it is what lets an artist shown first and
     * opened be told apart from one shown twentieth and opened, and one shown every visit and never
     * opened be told apart from one never shown at all.
     */
    CLICK
}
