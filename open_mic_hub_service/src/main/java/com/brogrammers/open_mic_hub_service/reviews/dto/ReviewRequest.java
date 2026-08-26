package com.brogrammers.open_mic_hub_service.reviews.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewRequest {
    private Long bookingId;
    private Long artistId;
    private int rating;
    private String comment;
}
