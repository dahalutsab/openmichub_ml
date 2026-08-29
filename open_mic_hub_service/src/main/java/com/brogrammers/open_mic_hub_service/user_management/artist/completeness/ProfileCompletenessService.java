package com.brogrammers.open_mic_hub_service.user_management.artist.completeness;

import com.brogrammers.open_mic_hub_service.social_feed.repository.PostRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository.ArtistAvailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * What is still missing from an artist's profile, and why it matters.
 *
 * <p>A checklist of empty fields would be busywork. What makes this worth showing is that the
 * items are the ones that decide whether an artist gets found and booked at all, and they are
 * weighted by how much each one actually does:
 *
 * <ul>
 *   <li><b>Availability</b> carries the most weight and is the only blocking item, because it is
 *       not a matter of presentation. Booking checks the requested day against published hours and
 *       refuses when there are none, so an artist without any cannot be booked whatever else their
 *       profile says.</li>
 *   <li><b>Genres and bio</b> come next because they are what search runs on. Both feed the
 *       embedding an artist is matched against, and the ranking model gives genre the largest
 *       share of its weight, so an artist with neither is close to invisible to a text search.</li>
 *   <li><b>A rate</b> matters because the ranker scores price against the organizer's budget. Left
 *       at zero it does not read as free — it reads as unstated, and price is worth roughly a
 *       fifth of the fit score.</li>
 * </ul>
 *
 * <p>The weights are a judgement, not a measurement, and they are stated here rather than buried
 * so they can be argued with.
 */
@Service
@RequiredArgsConstructor
public class ProfileCompletenessService {

    /** A bio shorter than this is a placeholder rather than a description. */
    private static final int MEANINGFUL_BIO_LENGTH = 40;

    private final ArtistRepository artistRepository;
    private final ArtistAvailabilityRepository availabilityRepository;
    private final PostRepository postRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    @Transactional(readOnly = true)
    public ProfileCompleteness forCurrentArtist() {
        UserEntity user = loggedInUserUtil.getLoggedInUser();
        Artist artist = artistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("No artist profile for this account."));
        return evaluate(artist, user);
    }

    ProfileCompleteness evaluate(Artist artist, UserEntity user) {
        boolean hasAvailability = availabilityRepository.findAllByArtist(artist).stream()
                .map(ArtistAvailability::getAvailabilityTimes)
                .anyMatch(times -> times != null && !times.isEmpty());
        long posts = postRepository.countByArtist_Id(artist.getId());

        List<ProfileChecklistItem> items = new ArrayList<>();

        items.add(new ProfileChecklistItem(
                "availability",
                "Publish your weekly availability",
                "Nothing else counts until this is set. A booking request is checked against the "
                        + "hours you have published for that weekday, and refused when there are "
                        + "none — so an empty calendar means you cannot be booked at all.",
                "/artist/calender", 25, hasAvailability, true));

        items.add(new ProfileChecklistItem(
                "genres",
                "Choose the styles you perform",
                "This is the single strongest signal in search. Organizers filter by it, and it "
                        + "carries the largest share of the ranking model's weight — without it you "
                        + "are missing from most results.",
                "/artist/profile", 20,
                artist.getGenres() != null && !artist.getGenres().isEmpty(), false));

        items.add(new ProfileChecklistItem(
                "bio",
                "Write a few lines about your act",
                "Search matches on meaning, not keywords: a query like \"something mellow for a "
                        + "restaurant opening\" is compared against your own words. A short or "
                        + "missing bio gives it nothing to match.",
                "/artist/profile", 15,
                artist.getBio() != null && artist.getBio().trim().length() >= MEANINGFUL_BIO_LENGTH,
                false));

        items.add(new ProfileChecklistItem(
                "rate",
                "Set your hourly rate",
                "Organizers search with a budget, and results are scored on how well your rate "
                        + "fits it. Left at zero it does not read as free, it reads as unstated.",
                "/artist/profile", 15, artist.getHourlyRate() > 0, false));

        items.add(new ProfileChecklistItem(
                "photo",
                "Add a profile photo",
                "Your photo is the first thing on every search result and card. A profile without "
                        + "one gets passed over before it is read.",
                "/artist/profile", 10,
                isPresent(user.getProfileImage()), false));

        items.add(new ProfileChecklistItem(
                "location",
                "Say where you are based",
                "Distance is part of how results are ranked, and organizers filter by city. "
                        + "Without one you will not appear in a search for your own town.",
                "/artist/profile", 8, isPresent(user.getLocation()), false));

        items.add(new ProfileChecklistItem(
                "posts",
                "Share your first post",
                "Photos and clips from past nights are what an organizer looks at once your "
                        + "profile has caught their eye. Posts show up on your public page.",
                "/artist/posts/create", 7, posts > 0, false));

        int score = items.stream().filter(ProfileChecklistItem::done)
                .mapToInt(ProfileChecklistItem::weight).sum();

        return new ProfileCompleteness(score, score >= 100, hasAvailability, items);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
