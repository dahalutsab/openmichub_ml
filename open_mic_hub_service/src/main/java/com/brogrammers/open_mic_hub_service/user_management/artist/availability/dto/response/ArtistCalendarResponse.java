package com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.dto.response.UnAvailabilityResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtistCalendarResponse {
    private Long artistId;
    private List<AvailabilityResponse> availabilities;
    private List<UnAvailabilityResponse> unavailabilities;
}