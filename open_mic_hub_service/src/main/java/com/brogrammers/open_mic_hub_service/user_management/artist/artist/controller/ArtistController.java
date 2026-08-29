package com.brogrammers.open_mic_hub_service.user_management.artist.artist.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.ArtistService;
import com.brogrammers.open_mic_hub_service.user_management.artist.completeness.ProfileCompletenessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

@RestController
@RequestMapping(API_BASE + "/artist")
@RequiredArgsConstructor
public class ArtistController extends BaseController {
    private final ArtistService artistService;
    private final ProfileCompletenessService profileCompletenessService;

    @PreAuthorize("hasRole('ARTIST')")
    @GetMapping
    public ResponseEntity<GlobalApiResponse> getLoggedInArtist() {
        return successResponse(
            artistService.getLoggedInArtist(),
            "Fetched logged-in artist successfully."
        );
    }

    /**
     * What is still missing from this artist's profile, and why each item matters.
     *
     * <p>Weighted rather than a plain count of empty fields: an artist with no published
     * availability cannot be booked at all, which is not the same kind of gap as a missing photo.
     */
    @PreAuthorize("hasRole('ARTIST')")
    @GetMapping("/profile-completeness")
    public ResponseEntity<GlobalApiResponse> getProfileCompleteness() {
        return successResponse(
            profileCompletenessService.forCurrentArtist(),
            "Fetched profile completeness successfully."
        );
    }

    //update hourly rate of artist
    @PreAuthorize("hasRole('ARTIST')")
    @PatchMapping("/hourly-rate")
    public ResponseEntity<GlobalApiResponse> updateArtistHourlyRate(@RequestParam double hourlyRate) {
        return successResponse(
            artistService.updateArtistHourlyRate(hourlyRate),
            "Updated artist hourly rate successfully."
        );
    }
}
