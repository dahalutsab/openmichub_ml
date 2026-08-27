package com.brogrammers.open_mic_hub_service.reviews.repository;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.reviews.entity.Review;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Page<Review>> findAllByArtist(Artist artist, Pageable pageable);
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.artist = :artist")
    Double findAverageRatingByArtist(@Param("artist") Artist artist);

    boolean existsByBookingAndReviewer(Booking booking, UserEntity reviewer);

    Page<Review> findAllByArtistOrderByCreatedDateDesc(Artist artist, Pageable pageable);

    Page<Review> findAllByReviewerOrderByCreatedDateDesc(UserEntity reviewer, Pageable pageable);

    /**
     * How many reviews an artist has at each star rating.
     *
     * <p>Returns {@code [rating, count]} rows. The client used to derive its average and its
     * "N reviews" line from whichever page it had loaded, so an artist with twenty reviews
     * advertised ten, and the figure disagreed with the rating on the same screen.
     */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.artist.id = :artistId GROUP BY r.rating")
    List<Object[]> countByRatingForArtist(@Param("artistId") Long artistId);
}
