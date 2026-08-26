package com.brogrammers.open_mic_hub_service.booking.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingRequest {
    private String venue;
    private LocalDate eventDate;
    @JsonDeserialize(using = LocalTimeDeserializer.class)
    private LocalTime startTime;

    @JsonDeserialize(using = LocalTimeDeserializer.class)
    private LocalTime endTime;
    private String eventType;
    private Long artistId;

}
