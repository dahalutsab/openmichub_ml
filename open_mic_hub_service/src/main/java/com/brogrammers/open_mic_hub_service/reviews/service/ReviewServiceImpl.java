package com.brogrammers.open_mic_hub_service.reviews.service;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.reviews.dto.ReviewRequest;
import com.brogrammers.open_mic_hub_service.reviews.dto.RatingSummaryResponse;
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
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService{
    private final ReviewRepository reviewRepository;
    private final ArtistRepository artistRepository;
    private final BookingRepository bookingRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    /**
     * Records a review of an artist for a booking the reviewer actually made.
     *
     * <p>Previously this trusted whatever booking and artist ids arrived in the request, checked
     * nothing about who was reviewing, and allowed unlimited reviews per booking — so anyone could
     * drive any artist's rating in either direction.
     */
    @Override
    public ReviewResponse createReview(ReviewRequest reviewRequest) {
        log.info("Creating review for booking ID: {}", reviewRequest.getBookingId());
        UserEntity reviewer = loggedInUserUtil.getLoggedInUser();

        Booking booking = bookingRepository.findById(reviewRequest.getBookingId())
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (!booking.getUserId().getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("You can only review your own bookings.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("You can only review a booking that went ahead.");
        }
        if (reviewRepository.existsByBookingAndReviewer(booking, reviewer)) {
            throw new IllegalArgumentException("You have already reviewed this booking.");
        }

        // The artist is taken from the booking, not from the request.
        Artist artist = booking.getArtistId();

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

    /** Only the author may edit a review, and only its rating and comment can change. */
    @Override
    public ReviewResponse updateReview(Long reviewId, ReviewRequest reviewRequest) {
        log.info("Updating review with ID: {}", reviewId);
        Review review = requireOwnReview(reviewId);

        review.setRating(validateRating(reviewRequest.getRating()));
        review.setComment(reviewRequest.getComment());
        reviewRepository.save(review);

        Artist artist = review.getArtist();
        artist.setRating(calculateAverageRating(artist));
        artistRepository.save(artist);

        return new ReviewResponse(review);
    }

    /**
     * Loads a review and confirms the logged-in user wrote it.
     *
     * <p>update and delete previously took only an id, so any authenticated caller could rewrite or
     * remove anyone's review — and update also let them repoint it at a different artist.
     */
    private boolean isPlatformStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_SUPER_ADMIN") || a.equals("ROLE_ADMIN"));
    }

    private Review requireOwnReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        UserEntity loggedInUser = loggedInUserUtil.getLoggedInUser();
        if (!review.getReviewer().getId().equals(loggedInUser.getId())) {
            throw new AccessDeniedException("This review is not yours.");
        }
        return review;
    }

    /** The author may remove their own review; platform staff may remove any, for moderation. */
    @Override
    public void deleteReview(Long reviewId) {
        log.info("Deleting review with ID: {}", reviewId);
        Review review = isPlatformStaff()
                ? reviewRepository.findById(reviewId)
                        .orElseThrow(() -> new EntityNotFoundException("Review not found"))
                : requireOwnReview(reviewId);
        Artist artist = review.getArtist();

        reviewRepository.delete(review);

        // Keep the cached average honest after a review disappears.
        artist.setRating(calculateAverageRating(artist));
        artistRepository.save(artist);
    }

    @Override
    public Page<ReviewResponse> getAllReviews(Pageable pageable) {
        return reviewRepository.findAll(pageable)
                .map(ReviewResponse::new);
    }

    /** An artist having no reviews yet is an empty page, not an error. */
    @Override
    public Page<ReviewResponse> getReviewsByArtistId(Long artistId, Pageable pageable) {
        log.info("Fetching reviews for artist with ID: {}", artistId);
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new EntityNotFoundException("Artist not found"));

        return reviewRepository.findAllByArtistOrderByCreatedDateDesc(artist, pageable)
                .map(ReviewResponse::new);
    }

    @Override
    public Page<ReviewResponse> getAllCurrentArtistReviews(Pageable pageable) {
        log.info("Fetching all reviews for the current artist");
        Artist currentArtist = loggedInUserUtil.getLoggedInArtist();
        return reviewRepository.findAllByArtistOrderByCreatedDateDesc(currentArtist, pageable)
                .map(ReviewResponse::new);
    }

    @Override
    public Page<ReviewResponse> getMyReviews(Pageable pageable) {
        UserEntity reviewer = loggedInUserUtil.getLoggedInUser();
        return reviewRepository.findAllByReviewerOrderByCreatedDateDesc(reviewer, pageable)
                .map(ReviewResponse::new);
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

    /**
     * Aggregates in the database rather than over a loaded page.
     *
     * <p>One grouped query gives both the distribution and, from it, the mean and the total, so
     * the three can never disagree with one another.
     */
    @Override
    @Transactional(readOnly = true)
    public RatingSummaryResponse getArtistRatingSummary(Long artistId) {
        long[] buckets = new long[5];
        long total = 0;
        long weighted = 0;

        for (Object[] row : reviewRepository.countByRatingForArtist(artistId)) {
            int stars = Math.min(Math.max(((Number) row[0]).intValue(), 1), 5);
            long count = ((Number) row[1]).longValue();
            buckets[stars - 1] += count;
            total += count;
            weighted += (long) stars * count;
        }

        double average = total == 0 ? 0 : Math.round(((double) weighted / total) * 10) / 10.0;
        return new RatingSummaryResponse(average, total, List.of(
                buckets[0], buckets[1], buckets[2], buckets[3], buckets[4]));
    }
}
