package com.brogrammers.open_mic_hub_service.user_management.artist.artist.service;

import com.brogrammers.open_mic_hub_service.user_management.artist.dto.response.ArtistResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArtistService {
    Page<ArtistResponse> getAllVerifiedArtists(Pageable pageable, String searchTerm, String genreName);
    //get logged in artist
    /** One verified artist by artist id, for the public profile page. */
    ArtistResponse getArtistById(Long artistId);

    /** The same profile, addressed by its public URL segment. */
    ArtistResponse getArtistBySlug(String slug);

    ArtistResponse getLoggedInArtist();

    ArtistResponse updateArtistHourlyRate(double hourlyRate);
}
