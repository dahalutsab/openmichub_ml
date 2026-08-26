package com.brogrammers.open_mic_hub_service.reviews.dto;

import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewRequest {

    @NotNull(message = "A booking id is required")
    private Long bookingId;

    /**
     * Ignored on write. The artist is taken from the booking so a review cannot be pointed at
     * someone the reviewer never hired. Kept for backwards compatibility with existing clients.
     */
    private Long artistId;

    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private int rating;

    @Size(max = 2000, message = "Comment must be 2000 characters or fewer")
    private String comment;
}
