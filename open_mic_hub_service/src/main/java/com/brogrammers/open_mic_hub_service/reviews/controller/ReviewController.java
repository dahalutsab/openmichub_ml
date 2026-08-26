package com.brogrammers.open_mic_hub_service.reviews.controller;

import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.reviews.dto.ReviewRequest;
import com.brogrammers.open_mic_hub_service.reviews.service.ReviewService;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

/**
 * Artist reviews.
 *
 * <p>Reads are public: ratings are what prospective clients browse artists on, so they have to be
 * visible without an account. Writes are restricted to the person who made the booking, and editing
 * or removing a review is limited to its author — enforced in the service, which resolves the
 * caller from the security context rather than from the request body.
 */
@RestController
@RequestMapping(API_BASE + "/reviews")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reviews", description = "Ratings and written feedback for artists")
public class ReviewController extends BaseController {

    private final ReviewService reviewService;

    @Operation(summary = "Review an artist",
            description = "Leaves a review for a confirmed booking. One review per booking, and only "
                    + "the organizer who made it may write one.")
    @PreAuthorize(UserRole.ANY_BOOKER)
    @PostMapping
    public ResponseEntity<GlobalApiResponse> createReview(@Valid @RequestBody ReviewRequest reviewRequest) {
        log.info("Review submitted for booking {}", reviewRequest.getBookingId());
        return successResponse(reviewService.createReview(reviewRequest),
                "Review submitted successfully", HttpStatus.CREATED);
    }

    @Operation(summary = "Reviews for an artist",
            description = "Public. Returns the reviews left for one artist, newest first.")
    @GetMapping("/artist/{artistId:\\d+}")
    public ResponseEntity<GlobalApiResponse> getReviewsByArtist(@PathVariable Long artistId,
                                                                Pageable pageable) {
        return successResponse(reviewService.getReviewsByArtistId(artistId, pageable),
                "Reviews fetched successfully");
    }

    // Numeric ids only, so this cannot swallow /me or /me/artist.
    @Operation(summary = "A single review", description = "Public.")
    @GetMapping("/{reviewId:\\d+}")
    public ResponseEntity<GlobalApiResponse> getReviewById(@PathVariable Long reviewId) {
        return successResponse(reviewService.getReviewById(reviewId), "Review fetched successfully");
    }

    @Operation(summary = "Reviews of the logged-in artist",
            description = "What clients have said about the artist making the request.")
    @PreAuthorize("hasRole('ARTIST')")
    @GetMapping("/me/artist")
    public ResponseEntity<GlobalApiResponse> getMyArtistReviews(Pageable pageable) {
        return successResponse(reviewService.getAllCurrentArtistReviews(pageable),
                "Reviews fetched successfully");
    }

    @Operation(summary = "Reviews written by the logged-in user")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<GlobalApiResponse> getMyReviews(Pageable pageable) {
        return successResponse(reviewService.getMyReviews(pageable), "Reviews fetched successfully");
    }

    @Operation(summary = "Edit a review", description = "Author only. Rating and comment only.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{reviewId:\\d+}")
    public ResponseEntity<GlobalApiResponse> updateReview(@PathVariable Long reviewId,
                                                          @Valid @RequestBody ReviewRequest reviewRequest) {
        return successResponse(reviewService.updateReview(reviewId, reviewRequest),
                "Review updated successfully");
    }

    @Operation(summary = "Delete a review",
            description = "The author may remove their own review; platform staff may remove any.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{reviewId:\\d+}")
    public ResponseEntity<GlobalApiResponse> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return successResponse(null, "Review deleted successfully");
    }

    @Operation(summary = "All reviews", description = "Platform staff only, for moderation.")
    @PreAuthorize(UserRole.ANY_ADMIN)
    @GetMapping
    public ResponseEntity<GlobalApiResponse> getAllReviews(Pageable pageable) {
        return successResponse(reviewService.getAllReviews(pageable), "Reviews fetched successfully");
    }
}
