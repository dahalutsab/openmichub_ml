package com.brogrammers.open_mic_hub_service.auth.dto.request;

import com.brogrammers.open_mic_hub_service.auth.dto.request.artist.ArtistGenreRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The answer to the one question a social sign-in leaves open.
 *
 * <p>Only {@code performing} is always required. The artist fields are validated in the service
 * rather than here, because whether they are required depends on that answer — annotations cannot
 * express "required, but only when this other field is true".
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompleteProfileRequest {

    /** True to join as an artist, false to join as an organizer who books them. */
    @NotNull(message = "Choose whether you are booking artists or performing.")
    private Boolean performing;

    @Size(max = 120, message = "That stage name is too long.")
    private String stageName;

    @Size(max = 2000, message = "That bio is too long.")
    private String bio;

    private String location;

    @PositiveOrZero(message = "An hourly rate cannot be negative.")
    private Double hourlyRate;

    private List<ArtistGenreRequest> genres;
}
