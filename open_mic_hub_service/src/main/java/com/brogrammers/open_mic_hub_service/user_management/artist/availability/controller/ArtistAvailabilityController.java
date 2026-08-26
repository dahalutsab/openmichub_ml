package com.brogrammers.open_mic_hub_service.user_management.artist.availability.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.request.AvailabilityRequest;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response.ArtistCalendarResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response.AvailabilityResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.service.ArtistAvailabilityService;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.dto.response.UnAvailabilityResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.entity.ArtistUnavailability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

@RestController
@RequestMapping(API_BASE + "/artist")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Artist Availability", description = "APIs for managing artist availability and unavailability")
public class ArtistAvailabilityController extends BaseController {
    private final ArtistAvailabilityService artistAvailabilityService;

    // --- AVAILABILITY ---

    @Operation(summary = "Add availability", description = "Add a new availability slot for the logged-in artist.")
    @PostMapping("/availability")
    public ResponseEntity<GlobalApiResponse> addAvailability(
            @RequestBody AvailabilityRequest request) {
        AvailabilityResponse result = artistAvailabilityService.addAvailability(request);
        return successResponse(result, "Availability added successfully.");
    }

    @Operation(summary = "Update availability", description = "Update an existing availability slot for the logged-in artist.")
    @PutMapping("/availability/{availabilityId}")
    public ResponseEntity<GlobalApiResponse> updateAvailability(
            @Parameter(description = "Availability ID", required = true) @PathVariable Long availabilityId,
            @RequestBody AvailabilityRequest request) {
        AvailabilityResponse result = artistAvailabilityService.updateAvailability(availabilityId, request);
        return successResponse(result, "Availability updated successfully.");
    }

    @Operation(summary = "Delete availability", description = "Delete an availability slot for the logged-in artist.")
    @DeleteMapping("/availability/{availabilityId}")
    public ResponseEntity<GlobalApiResponse> deleteAvailability(
            @Parameter(description = "Availability ID", required = true) @PathVariable Long availabilityId) {
        artistAvailabilityService.deleteAvailability(availabilityId);
        return successResponse(null, "Availability deleted successfully.");
    }

    @Operation(summary = "Get all availabilities", description = "Get all availability slots for the logged-in artist.")
    @GetMapping("/availability")
    public ResponseEntity<GlobalApiResponse> getAvailabilities() {
        List<AvailabilityResponse> result = artistAvailabilityService.getAvailabilities();
        return successResponse(result, "Availabilities fetched successfully.");
    }

    // --- UNAVAILABILITY ---

    @Operation(summary = "Add unavailability", description = "Add a new unavailability period for the logged-in artist.")
    @PostMapping("/unavailability")
    public ResponseEntity<GlobalApiResponse> addUnavailability(
            @RequestBody ArtistUnavailability request) {
        UnAvailabilityResponse result = artistAvailabilityService.addUnavailability(request);
        return successResponse(result, "Unavailability added successfully.");
    }

    @Operation(summary = "Delete unavailability", description = "Delete an unavailability period for the logged-in artist.")
    @DeleteMapping("/unavailability/{unavailabilityId}")
    public ResponseEntity<GlobalApiResponse> deleteUnavailability(
            @Parameter(description = "Unavailability ID", required = true) @PathVariable Long unavailabilityId) {
        artistAvailabilityService.deleteUnavailability(unavailabilityId);
        return successResponse(null, "Unavailability deleted successfully.");
    }

    @Operation(summary = "Get all unavailabilities", description = "Get all unavailability periods for the logged-in artist.")
    @GetMapping("/unavailability")
    public ResponseEntity<GlobalApiResponse> getUnavailabilities() {
        List<UnAvailabilityResponse> result = artistAvailabilityService.getUnavailabilities();
        return successResponse(result, "Unavailabilities fetched successfully.");
    }

    @Operation(summary = "Get calendar data by stage name", description = "Get all availabilities and unavailabilities for the artist by stage name for calendar display.")
    @GetMapping("/calendar/{stageName}")
    public ResponseEntity<GlobalApiResponse> getArtistCalendarByStageName(
            @Parameter(description = "Artist stage name", required = true) @PathVariable String stageName) {
        ArtistCalendarResponse result = artistAvailabilityService.getArtistCalendarByStageName(stageName);
        return successResponse(result, "Calendar data fetched successfully for artist: " + stageName);
    }
}