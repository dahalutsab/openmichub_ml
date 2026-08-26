package com.brogrammers.open_mic_hub_service.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

/**
 * One ranked artist.
 *
 * <p>The ML service speaks snake_case; the rest of this API speaks camelCase.
 * {@code @JsonAlias} accepts the incoming names without imposing them on the
 * response, so callers see one consistent convention.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtistHit {

    @JsonAlias("artist_id")
    private Long artistId;

    @JsonAlias("stage_name")
    private String stageName;

    @JsonAlias("full_name")
    private String fullName;

    private String bio;
    private String city;

    @JsonAlias("hourly_rate")
    private Double hourlyRate;

    private Double rating;

    @JsonAlias("completed_bookings")
    private Integer completedBookings;

    @JsonAlias("sub_genres")
    private List<String> subGenres;

    @JsonAlias("parent_genres")
    private List<String> parentGenres;

    @JsonAlias("profile_image")
    private String profileImage;

    /** Ranking score. Higher is a better fit for the request; not comparable across queries. */
    private Double score;

    /** Cosine similarity to the query text, absent when there was no text query. */
    private Double similarity;
}
