package com.brogrammers.open_mic_hub_service.reviews.service;

import com.brogrammers.open_mic_hub_service.reviews.dto.ReviewRequest;
import com.brogrammers.open_mic_hub_service.reviews.dto.RatingSummaryResponse;
import com.brogrammers.open_mic_hub_service.reviews.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    /** Every review an artist has, in aggregate. */
    RatingSummaryResponse getArtistRatingSummary(Long artistId);

    ReviewResponse createReview(ReviewRequest reviewRequest);
    ReviewResponse getReviewById(Long reviewId);
    ReviewResponse updateReview(Long reviewId, ReviewRequest reviewRequest);
    void deleteReview(Long reviewId);
    Page<ReviewResponse> getAllReviews(Pageable pageable);
    Page<ReviewResponse> getReviewsByArtistId(Long artistId, Pageable pageable);
    Page<ReviewResponse> getAllCurrentArtistReviews(Pageable pageable);

    /** Reviews written by the logged-in user. */
    Page<ReviewResponse> getMyReviews(Pageable pageable);
}
