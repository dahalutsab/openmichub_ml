package com.brogrammers.open_mic_hub_service.user_management.artist.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An artist as shown publicly.
 *
 * <p>Carries no email, phone or address: the profile page is readable without an
 * account, and booking goes through the platform rather than around it. What it
 * does carry is the evidence a booker actually decides on — what they play, what
 * they charge, how they are rated and how much they have played.
 */
@RequiredArgsConstructor
@AllArgsConstructor
@Data
public class ArtistResponse {

    /**
     * The artist id.
     *
     * <p>Its absence was a real fault rather than an omission: the browse screen
     * builds its links from this field, so every card pointed at
     * {@code /artists/undefined}.
     */
    private Long artistId;

    private String fullName;
    private String bio;
    private URI profilePictureUrl;
    private String stageName;
    private double hourlyRate;
    private List<GenreResponse> genre;

    /** Average of the reviews left for this artist, maintained on review write. */
    private Double rating;

    /** Where they are based. A business detail, unlike the contact fields. */
    private String city;

    /** When they joined, for "playing here since". */
    private LocalDateTime memberSince;

    /** Bookings that went ahead — the platform's proxy for experience. */
    private long completedBookings;

    /**
     * Share of requests the artist answered rather than let lapse, 0-100.
     *
     * <p>Null for an artist who has had no requests yet, so the profile can say
     * "no requests yet" instead of implying they ignore people.
     */
    private Double responseRate;

    public ArtistResponse(Artist artist, List<GenreResponse> genreResponses) {
        this.artistId = artist.getId();
        this.fullName = artist.getUser().getFullName();
        this.bio = artist.getBio();
        this.profilePictureUrl = FileUrlUtil.getFileUri(artist.getUser().getProfileImage());
        this.stageName = artist.getStageName();
        this.hourlyRate = artist.getHourlyRate();
        this.genre = genreResponses;
        this.rating = artist.getRating();
        this.city = artist.getUser().getLocation();
        this.memberSince = artist.getCreatedDate();
    }
}
