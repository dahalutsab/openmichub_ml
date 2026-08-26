package com.brogrammers.open_mic_hub_service.analytics.repository;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only aggregation queries behind the dashboards.
 *
 * <p>It hangs off {@link Booking} because Spring Data needs a managed root, but nothing here writes
 * and nothing here is used outside the analytics module.
 *
 * <p>Two shapes are used deliberately:
 *
 * <ul>
 *   <li><b>Grouped counts and sums</b> — status breakdowns, payment methods, the week/hour grid —
 *       are pushed into the database. They collapse many rows into a handful, so doing it in Java
 *       would mean dragging the whole table across for no reason.
 *   <li><b>Time series</b> come back as one row per record via {@code find*Rows}, and are bucketed
 *       in the service. Postgres could do it with {@code date_trunc}, but that needs a native query
 *       per bucket size; the row sets here are bounded by an explicit window, and keeping the
 *       bucketing in Java means month, week and day all share one code path.
 * </ul>
 *
 * <p>Every method is bounded by a date range — none of them can degrade into a full-table scan as
 * the platform grows.
 */
@Repository
public interface AnalyticsRepository extends JpaRepository<Booking, Long> {

    // ------------------------------------------------------------ platform --

    @Query("SELECT COUNT(u) FROM UserEntity u JOIN u.roles r WHERE r.name = :role")
    long countUsersByRole(@Param("role") String role);

    @Query("SELECT COUNT(u) FROM UserEntity u")
    long countAllUsers();

    @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.createdDate >= :from AND u.createdDate < :to")
    long countUsersRegisteredBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ------------------------------------------------------------ bookings --

    /**
     * Bookings in a window, as (eventDate, totalAmount, status, startTime).
     *
     * <p>Bucketed by event date rather than creation date: a dashboard about a booking platform is
     * about when the gigs happen, not when the row was written.
     */
    @Query("""
            SELECT b.eventDate, b.totalAmount, b.status, b.startTime
            FROM Booking b
            WHERE b.eventDate >= :from AND b.eventDate <= :to
            """)
    List<Object[]> findBookingRowsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT b.eventDate, b.totalAmount, b.status, b.startTime
            FROM Booking b
            WHERE b.artistId.id = :artistId AND b.eventDate >= :from AND b.eventDate <= :to
            """)
    List<Object[]> findArtistBookingRowsBetween(@Param("artistId") Long artistId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    @Query("""
            SELECT b.eventDate, b.totalAmount, b.status, b.startTime
            FROM Booking b
            WHERE b.userId.id = :userId AND b.eventDate >= :from AND b.eventDate <= :to
            """)
    List<Object[]> findBookerBookingRowsBetween(@Param("userId") Long userId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    // --------------------------------------------------- grouped breakdowns --

    @Query("""
            SELECT b.status, COUNT(b), COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b
            WHERE b.eventDate >= :from AND b.eventDate <= :to
            GROUP BY b.status
            """)
    List<Object[]> countBookingsByStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT b.status, COUNT(b), COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b
            WHERE b.artistId.id = :artistId AND b.eventDate >= :from AND b.eventDate <= :to
            GROUP BY b.status
            """)
    List<Object[]> countArtistBookingsByStatus(@Param("artistId") Long artistId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("""
            SELECT b.status, COUNT(b), COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b
            WHERE b.userId.id = :userId AND b.eventDate >= :from AND b.eventDate <= :to
            GROUP BY b.status
            """)
    List<Object[]> countBookerBookingsByStatus(@Param("userId") Long userId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("""
            SELECT b.eventType, COUNT(b), COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b
            WHERE b.userId.id = :userId AND b.eventDate >= :from AND b.eventDate <= :to
            GROUP BY b.eventType
            ORDER BY SUM(b.totalAmount) DESC
            """)
    List<Object[]> sumBookerSpendByEventType(@Param("userId") Long userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Bookings and value per artist, for the leaderboard.
     *
     * <p>Only settled statuses count — a leaderboard built from requests anyone can raise would
     * rank whoever is most often asked, not whoever actually plays.
     */
    @Query("""
            SELECT a.id, a.stageName, a.user.fullName, a.user.profileImage, a.rating,
                   COUNT(b), COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b JOIN b.artistId a
            WHERE b.eventDate >= :from AND b.eventDate <= :to
              AND b.status IN (com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus.CONFIRMED,
                               com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus.COMPLETED)
            GROUP BY a.id, a.stageName, a.user.fullName, a.user.profileImage, a.rating
            ORDER BY SUM(b.totalAmount) DESC
            """)
    List<Object[]> findTopArtistsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The artists one booker hires most, ordered by how often. */
    @Query("""
            SELECT a.id, a.stageName, a.user.fullName, a.user.profileImage, a.rating,
                   COUNT(b), COALESCE(SUM(b.totalAmount), 0)
            FROM Booking b JOIN b.artistId a
            WHERE b.userId.id = :userId
            GROUP BY a.id, a.stageName, a.user.fullName, a.user.profileImage, a.rating
            ORDER BY COUNT(b) DESC
            """)
    List<Object[]> findFavouriteArtists(@Param("userId") Long userId);

    // ------------------------------------------------------------ payments --

    @Query("""
            SELECT COALESCE(p.paymentMethod, 'UNKNOWN'), COUNT(p), COALESCE(SUM(p.receivedAmount), 0)
            FROM Payment p
            WHERE p.createdDate >= :from AND p.createdDate < :to
              AND p.paymentStatus = com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus.COMPLETED
            GROUP BY p.paymentMethod
            """)
    List<Object[]> countPaymentsByMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(p.paymentMethod, 'UNKNOWN'), COUNT(p), COALESCE(SUM(p.receivedAmount), 0)
            FROM Payment p
            WHERE p.userInfoEntity.id = :userId AND p.createdDate >= :from AND p.createdDate < :to
              AND p.paymentStatus = com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus.COMPLETED
            GROUP BY p.paymentMethod
            """)
    List<Object[]> countBookerPaymentsByMethod(@Param("userId") Long userId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** The platform's own take: system charges on settled payments. */
    @Query("""
            SELECT COALESCE(SUM(p.systemCharges), 0)
            FROM Payment p
            WHERE p.createdDate >= :from AND p.createdDate < :to
              AND p.paymentStatus = com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus.COMPLETED
            """)
    double sumPlatformFeesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // -------------------------------------------------------- transactions --

    @Query("""
            SELECT t.transactionType, COUNT(t), COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.createdDate >= :from AND t.createdDate < :to
            GROUP BY t.transactionType
            """)
    List<Object[]> countTransactionsByType(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT t.transactionPurpose, COUNT(t), COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.virtualCoin.artist.id = :artistId
              AND t.createdDate >= :from AND t.createdDate < :to
            GROUP BY t.transactionPurpose
            """)
    List<Object[]> countArtistTransactionsByPurpose(@Param("artistId") Long artistId,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);

    /** Credits to an artist's wallet in a window, as (createdDate, amount) — bucketed by the service. */
    @Query("""
            SELECT t.createdDate, t.amount
            FROM Transaction t
            WHERE t.virtualCoin.artist.id = :artistId
              AND t.transactionType = com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType.CREDIT
              AND t.createdDate >= :from AND t.createdDate < :to
            """)
    List<Object[]> findArtistEarningRowsBetween(@Param("artistId") Long artistId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.virtualCoin.artist.id = :artistId
              AND t.transactionType = com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType.CREDIT
            """)
    double sumArtistLifetimeEarnings(@Param("artistId") Long artistId);

    /**
     * Withdrawal value raised but not yet decided.
     *
     * <p>Platform-wide when {@code artistId} is null, one artist's otherwise — the two callers want
     * the identical filter, and duplicating it invites the pair to drift apart.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.transactionPurpose = com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose.WITHDRAWAL_REQUEST
              AND t.status = com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status.PENDING
              AND (:artistId IS NULL OR t.virtualCoin.artist.id = :artistId)
            """)
    double sumPendingWithdrawals(@Param("artistId") Long artistId);

    // ----------------------------------------------------------- row lists --

    /** Recent bookings across the platform, newest event first. */
    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.artistId a
            LEFT JOIN FETCH b.userId u
            WHERE b.eventDate <= :to
            ORDER BY b.eventDate DESC, b.startTime DESC
            """)
    List<Booking> findRecentBookings(@Param("to") LocalDate to);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.artistId a
            LEFT JOIN FETCH b.userId u
            WHERE b.artistId.id = :artistId AND b.eventDate >= :from
            ORDER BY b.eventDate ASC, b.startTime ASC
            """)
    List<Booking> findArtistUpcomingBookings(@Param("artistId") Long artistId, @Param("from") LocalDate from);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.artistId a
            LEFT JOIN FETCH b.userId u
            WHERE b.userId.id = :userId AND b.eventDate >= :from
            ORDER BY b.eventDate ASC, b.startTime ASC
            """)
    List<Booking> findBookerUpcomingBookings(@Param("userId") Long userId, @Param("from") LocalDate from);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.artistId a
            LEFT JOIN FETCH b.userId u
            WHERE b.userId.id = :userId AND b.eventDate < :from
            ORDER BY b.eventDate DESC, b.startTime DESC
            """)
    List<Booking> findBookerPastBookings(@Param("userId") Long userId, @Param("from") LocalDate from);
}
