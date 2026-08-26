package com.brogrammers.open_mic_hub_service.reviews.dto;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.reviews.entity.Review;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReviewResponse {
    private Long reviewId;
    private Booking booking;
    private UserEntity reviewer;
    private Artist artist;
    private int rating;
    private String comment;

    public ReviewResponse(Review review){
        this.reviewId = review.getReviewId();
        this.booking = review.getBooking();
        this.reviewer = review.getReviewer();
        this.artist = review.getArtist();
        this.rating = review.getRating();
        this.comment = review.getComment();
    }
}
