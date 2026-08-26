package com.brogrammers.open_mic_hub_service.reviews.dto;

import com.brogrammers.open_mic_hub_service.reviews.entity.Review;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * A review as shown to the public.
 *
 * <p>This used to serialise the {@code Review}'s entity graph directly — the whole
 * {@code UserEntity}, {@code Artist} and {@code Booking}. {@code UserEntity} carries the bcrypt
 * password hash, phone number and address, none of which are annotated {@code @JsonIgnore}, so
 * exposing reviews publicly in that shape would have published every reviewer's credentials.
 * Only the fields a reader needs are carried now.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Long reviewId;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    private Long bookingId;
    private String eventType;

    private Long reviewerId;
    private String reviewerName;
    private URI reviewerImage;

    private Long artistId;
    private String artistStageName;

    public ReviewResponse(Review review) {
        this.reviewId = review.getReviewId();
        this.rating = review.getRating();
        this.comment = review.getComment();
        this.createdAt = review.getCreatedDate();

        if (review.getBooking() != null) {
            this.bookingId = review.getBooking().getId();
            this.eventType = review.getBooking().getEventType();
        }
        if (review.getReviewer() != null) {
            this.reviewerId = review.getReviewer().getId();
            this.reviewerName = review.getReviewer().getFullName();
            this.reviewerImage = FileUrlUtil.getFileUri(review.getReviewer().getProfileImage());
        }
        if (review.getArtist() != null) {
            this.artistId = review.getArtist().getId();
            this.artistStageName = review.getArtist().getStageName();
        }
    }
}
