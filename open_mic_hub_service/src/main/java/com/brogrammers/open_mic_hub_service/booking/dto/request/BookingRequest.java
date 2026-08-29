package com.brogrammers.open_mic_hub_service.booking.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A request to book an artist for one slot.
 *
 * <p>Nothing here was constrained before, so a request could arrive with no artist, no date and no
 * times and get as far as the service before failing on a null pointer. The relationship between
 * the two times cannot be expressed with field annotations and is checked in
 * {@code BookingServiceImpl}, alongside the availability rules it belongs with.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingRequest {

    @NotBlank(message = "A venue is required.")
    @Size(max = 255, message = "The venue name is too long.")
    private String venue;

    @NotNull(message = "An event date is required.")
    private LocalDate eventDate;

    @NotNull(message = "A start time is required.")
    @JsonDeserialize(using = LocalTimeDeserializer.class)
    private LocalTime startTime;

    @NotNull(message = "An end time is required.")
    @JsonDeserialize(using = LocalTimeDeserializer.class)
    private LocalTime endTime;

    @NotBlank(message = "An event type is required.")
    private String eventType;

    @NotNull(message = "An artist must be chosen.")
    private Long artistId;
}
