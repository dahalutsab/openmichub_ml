package com.brogrammers.open_mic_hub_service.user_management.artist.completeness;

import java.util.List;

/**
 * How ready an artist's profile is to be found and booked.
 *
 * @param score     0-100, weighted by how much each item affects being booked
 * @param complete  whether everything on the list is done
 * @param bookable  whether the profile can currently take a booking at all
 * @param items     the full list, done and not, so the client can show progress rather than only
 *                  what is missing
 */
public record ProfileCompleteness(
        int score,
        boolean complete,
        boolean bookable,
        List<ProfileChecklistItem> items) {
}
