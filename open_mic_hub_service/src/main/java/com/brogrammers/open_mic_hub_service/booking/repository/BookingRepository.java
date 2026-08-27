package com.brogrammers.open_mic_hub_service.booking.repository;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findAllByUserId(UserEntity userId, Pageable pageable);

    Page<Booking> findAllByArtistIdAndStatus(Artist artist, BookingStatus status, Pageable pageable);

    /**
     * Booking counts per artist: how many went ahead, and how many requests were answered.
     *
     * <p>One grouped query for a whole page of artists rather than one per artist. Answered means
     * decided either way — an artist who declines promptly is responsive, not unresponsive; only
     * requests left to lapse count against them.
     *
     * <p>Returns {@code [artistId, wentAhead, answered, total]}.
     */
    @Query("""
            SELECT b.artistId.id,
                   SUM(CASE WHEN b.status IN (com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus.CONFIRMED,
                                              com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus.COMPLETED)
                            THEN 1L ELSE 0L END),
                   SUM(CASE WHEN b.status <> com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus.PENDING
                            THEN 1L ELSE 0L END),
                   COUNT(b)
            FROM Booking b
            WHERE b.artistId.id IN :artistIds
            GROUP BY b.artistId.id
            """)
    List<Object[]> findBookingStatsFor(@Param("artistIds") Collection<Long> artistIds);

    /**
     * Whether the artist already has a live booking overlapping the given slot.
     *
     * <p>Two intervals overlap when each starts before the other ends. Touching at an endpoint —
     * one booking ending exactly when the next begins — is not an overlap.
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.artistId = :artist
              AND b.eventDate = :eventDate
              AND b.status IN :liveStatuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("artist") Artist artist,
                              @Param("eventDate") LocalDate eventDate,
                              @Param("startTime") LocalTime startTime,
                              @Param("endTime") LocalTime endTime,
                              @Param("liveStatuses") Collection<BookingStatus> liveStatuses);
}
