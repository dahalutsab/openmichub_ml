package com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.entity.ArtistUnavailability;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnAvailabilityResponse {
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    public UnAvailabilityResponse(ArtistUnavailability artistUnavailability) {
        this.id = artistUnavailability.getId();
        this.startDate = artistUnavailability.getStartDate();
        this.endDate = artistUnavailability.getEndDate();
        this.reason = artistUnavailability.getReason();
    }
}
