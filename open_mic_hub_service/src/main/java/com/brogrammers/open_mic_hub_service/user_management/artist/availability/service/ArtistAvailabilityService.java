package com.brogrammers.open_mic_hub_service.user_management.artist.availability.service;

import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.request.AvailabilityRequest;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response.ArtistCalendarResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response.AvailabilityResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.dto.response.UnAvailabilityResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.entity.ArtistUnavailability;

import java.util.List;

public interface ArtistAvailabilityService {
    AvailabilityResponse addAvailability(AvailabilityRequest request);
    AvailabilityResponse updateAvailability(Long availabilityId, AvailabilityRequest request);
    void deleteAvailability(Long availabilityId);
    List<AvailabilityResponse> getAvailabilities();

    UnAvailabilityResponse addUnavailability(ArtistUnavailability unavailability);
    void deleteUnavailability(Long unavailabilityId);
    List<UnAvailabilityResponse> getUnavailabilities();

    List<AvailabilityResponse> getAvailabilitiesByStageName(String stageName);

    List<UnAvailabilityResponse> getUnavailabilitiesByStageName(String stageName);

    ArtistCalendarResponse getArtistCalendarByStageName(String stageName);
}