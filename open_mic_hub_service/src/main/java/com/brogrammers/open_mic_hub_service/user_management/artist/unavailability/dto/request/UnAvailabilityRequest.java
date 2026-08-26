package com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnAvailabilityRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
}
