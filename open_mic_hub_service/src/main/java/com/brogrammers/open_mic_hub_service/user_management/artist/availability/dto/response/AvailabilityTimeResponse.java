package com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response;

import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.AvailabilityTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailabilityTimeResponse {
    private Long id;
    private LocalTime startTime;
    private LocalTime endTime;

    public AvailabilityTimeResponse(AvailabilityTime availabilityTime) {
        this.id = availabilityTime.getId();
        this.startTime = availabilityTime.getStartTime();
        this.endTime = availabilityTime.getEndTime();
    }
}
