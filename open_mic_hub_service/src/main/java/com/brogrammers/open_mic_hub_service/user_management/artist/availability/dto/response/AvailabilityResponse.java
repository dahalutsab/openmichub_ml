package com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailabilityResponse {
    private Long id;
    private DayOfWeek dayOfWeek;
    private List<AvailabilityTimeResponse> availabilityTimes;

    public AvailabilityResponse(ArtistAvailability artistAvailability) {
        this.id = artistAvailability.getId();
        this.dayOfWeek = artistAvailability.getDayOfWeek();
        this.availabilityTimes = artistAvailability.getAvailabilityTimes().stream()
                .map(AvailabilityTimeResponse::new)
                .toList();
    }
}
