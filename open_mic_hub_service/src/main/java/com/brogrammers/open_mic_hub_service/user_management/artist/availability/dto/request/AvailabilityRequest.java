package com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailabilityRequest {
    private DayOfWeek dayOfWeek;
    private List<AvailabilityTimeRequest> availabilityTimes;
}
