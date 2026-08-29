package com.brogrammers.open_mic_hub_service.user_management.artist.completeness;

/**
 * One thing an artist can do to make their profile more likely to get booked.
 *
 * @param key      stable identifier, so the client can key on it without matching on prose
 * @param label    what to do, in the imperative
 * @param hint     why it is worth doing — the part that turns a checklist into advice
 * @param action   where in the app to go and do it
 * @param weight   share of the completeness score, out of 100
 * @param done     whether it is already done
 * @param blocking whether leaving it undone stops the artist being booked at all
 */
public record ProfileChecklistItem(
        String key,
        String label,
        String hint,
        String action,
        int weight,
        boolean done,
        boolean blocking) {
}
