package com.brogrammers.open_mic_hub_service.reviews.service;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.reviews.dto.ReviewRequest;
import com.brogrammers.open_mic_hub_service.reviews.dto.ReviewResponse;
import com.brogrammers.open_mic_hub_service.reviews.entity.Review;
import com.brogrammers.open_mic_hub_service.reviews.repository.ReviewRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService{
    private final ReviewRepository reviewRepository;
    private final ArtistRepository artistRepository;
    private final BookingRepository bookingRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    @Override
    public ReviewResponse createReview(ReviewRequest reviewRequest) {
        log.info("Creating review for booking ID: {}", reviewRequest.getBookingId());
        // Validate booking
        Booking booking = bookingRepository.findById(reviewRequest.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        // Validate artist
        Artist artist = artistRepository.findById(reviewRequest.getArtistId())
                .orElseThrow(() -> new IllegalArgumentException("Artist not found for ID: " + reviewRequest.getArtistId()));
        // Validate reviewer
        UserEntity reviewer = loggedInUserUtil.getLoggedInUser();

        // Create and save the review
        Review review = new Review();
        review.setBooking(booking);
        review.setArtist(artist);
        review.setReviewer(reviewer);
        review.setRating(validateRating(reviewRequest.getRating()));
        review.setComment(reviewRequest.getComment());

        reviewRepository.save(review);
        log.info("Review created successfully with ID: {}", review.getReviewId());

        // Calculate and log the average rating for the artist
        double averageRating = calculateAverageRating(artist);

        // Update the artist's average rating
        artist.setRating(averageRating);
        artistRepository.save(artist);


        // Return the response
        return new ReviewResponse(review);
    }

    private int validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        return rating;
    }

    @Override
    public ReviewResponse getReviewById(Long reviewId) {
        log.info("Fetching review with ID: {}", reviewId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found for ID: " + reviewId));
        return new ReviewResponse(review);
    }

    @Override
    public ReviewResponse updateReview(Long reviewId, ReviewRequest reviewRequest) {
        log.info("Updating review with ID: {}", reviewId);
        Booking booking = bookingRepository.findById(reviewRequest.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        Artist artist = artistRepository.findById(reviewRequest.getArtistId())
                .orElseThrow(() -> new IllegalArgumentException("Artist not found"));

        UserEntity reviewer = loggedInUserUtil.getLoggedInUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        review.setBooking(booking);
        review.setArtist(artist);
        review.setReviewer(reviewer);
        review.setRating(validateRating(reviewRequest.getRating()));
        review.setComment(reviewRequest.getComment());
        reviewRepository.save(review);

        // Calculate and log the average rating for the artist
        double averageRating = calculateAverageRating(artist);

        // Update the artist's average rating
        artist.setRating(averageRating);
        artistRepository.save(artist);

        return new ReviewResponse(review);
    }

    @Override
    public void deleteReview(Long reviewId) {
        log.info("Deleting review with ID: {}", reviewId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        reviewRepository.delete(review);
    }

    @Override
    public Page<ReviewResponse> getAllReviews(Pageable pageable) {
        return reviewRepository.findAll(pageable)
                .map(ReviewResponse::new);
    }

    @Override
    public Page<ReviewResponse> getReviewsByArtistId(Long artistId, Pageable pageable) {
        log.info("Fetching reviews for artist with ID: {}", artistId);
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artist not found"));

        Page<Review> reviews = reviewRepository.findAllByArtist(artist, pageable)
                .orElseThrow(
                        () -> new IllegalArgumentException("No reviews found for this artist")
                );
        return reviews.map(ReviewResponse::new);
    }

    @Override
    public Page<ReviewResponse> getAllCurrentArtistReviews(Pageable pageable) {
        log.info("Fetching all reviews for the current artist");
        UserEntity currentUser = loggedInUserUtil.getLoggedInUser();
        Artist currentArtist = artistRepository.findByUser(currentUser)
                .orElseThrow(() -> new IllegalArgumentException("Current user is not an artist"));

        Page<Review> reviews = reviewRepository.findAllByArtist(currentArtist, pageable)
                .orElseThrow(
                        () -> new IllegalArgumentException("No reviews found for the current artist")
                );
        return reviews.map(ReviewResponse::new);
    }

    // Calculate the average rating for an artist
    public double calculateAverageRating(Artist artist) {
        log.info("Calculating average rating for artist with ID: {}", artist.getId());

        Double averageRating = reviewRepository.findAverageRatingByArtist(artist);

        if (averageRating == null) {
            log.warn("No reviews found for artist ID: {}, So providing default", artist.getId());
            return 3.0; // Return 3.0 if no reviews exist
        }

        if (averageRating < 1 || averageRating > 5) {
            log.warn("Average rating out of bounds for artist ID: {}, resetting to default", artist.getId());
            return 3.0; // Reset to default if the average rating is out of bounds
        }

        log.info("Average rating for artist ID {}: {}", artist.getId(), averageRating);
        return averageRating;
    }
}
