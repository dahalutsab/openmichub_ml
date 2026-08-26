package com.brogrammers.open_mic_hub_service.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Shapes returned by the analytics endpoints.
 *
 * <p>Each dashboard is served by a single overview call rather than one request per widget: the
 * panels all read from the same handful of tables over the same window, so splitting them up would
 * mean re-scanning bookings and payments five times to draw one screen.
 *
 * <p>All of these are records — the payloads are read-only projections, never persisted, and never
 * mutated after assembly.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    // ---------------------------------------------------------------- pieces --

    /**
     * One bucket of a time series.
     *
     * @param period  bucket label, ISO-8601: {@code 2026-08} for a month, {@code 2026-08-26} for a day
     * @param date    first day of the bucket, so the client can sort and format without parsing labels
     * @param primary the main measure — money for revenue series, a count for volume series
     * @param secondary a companion measure drawn against the same axis (net against gross, say)
     */
    public record SeriesPoint(String period, LocalDate date, double primary, double secondary) {
    }

    /** A labelled slice: booking statuses, payment methods, transaction types. */
    public record Slice(String label, long count, double amount) {
    }

    /**
     * One cell of the week/hour activity grid.
     *
     * @param dayOfWeek 1 (Monday) through 7 (Sunday), matching {@link java.time.DayOfWeek#getValue()}
     * @param hour      hour of day, 0–23, taken from the booking's start time
     */
    public record HeatCell(int dayOfWeek, int hour, long count) {
    }

    /** A figure with the change against the preceding window of equal length. */
    public record Metric(double value, double previousValue, double changePercent) {

        /**
         * Percentage change, guarding the divide-by-zero that a first-ever period produces.
         * A rise from nothing is reported as +100% rather than infinity.
         */
        public static Metric of(double current, double previous) {
            double change;
            if (previous == 0d) {
                change = current == 0d ? 0d : 100d;
            } else {
                change = ((current - previous) / Math.abs(previous)) * 100d;
            }
            return new Metric(current, previous, Math.round(change * 10d) / 10d);
        }
    }

    /** A leaderboard row on the admin board. */
    public record TopArtist(Long artistId,
                            String stageName,
                            String fullName,
                            String profileImage,
                            long bookings,
                            double earnings,
                            Double rating) {
    }

    /** A compact booking row, for the "recent"/"upcoming" tables. */
    public record BookingRow(Long id,
                             LocalDate eventDate,
                             String startTime,
                             String endTime,
                             String venue,
                             String eventType,
                             String status,
                             double totalAmount,
                             String artistStageName,
                             String bookerName) {
    }

    // ------------------------------------------------------------- overviews --

    /**
     * Platform-wide board.
     *
     * @param revenue      gross booking value settled in the window
     * @param platformFees the platform's own cut, from payment system charges
     * @param pendingPayouts value of withdrawal requests raised but not yet released
     */
    public record AdminOverview(int windowDays,
                                Metric revenue,
                                Metric bookings,
                                Metric newUsers,
                                Metric platformFees,
                                double pendingPayouts,
                                long totalUsers,
                                long totalArtists,
                                long totalBookers,
                                List<SeriesPoint> revenueSeries,
                                List<SeriesPoint> bookingsSeries,
                                List<Slice> bookingsByStatus,
                                List<Slice> paymentsByMethod,
                                List<Slice> transactionsByType,
                                List<HeatCell> activityHeatmap,
                                List<TopArtist> topArtists,
                                List<BookingRow> recentBookings) {
    }

    /**
     * A performer's own board.
     *
     * @param balance     spendable wallet balance right now
     * @param heldFunds   balance reserved against withdrawal requests still awaiting approval
     * @param acceptanceRate share of decided requests the artist accepted, 0–100
     */
    public record ArtistOverview(int windowDays,
                                 String stageName,
                                 String fullName,
                                 String profileImage,
                                 Double rating,
                                 double balance,
                                 double heldFunds,
                                 Metric earnings,
                                 Metric bookings,
                                 double lifetimeEarnings,
                                 long pendingRequests,
                                 double acceptanceRate,
                                 List<SeriesPoint> earningsSeries,
                                 List<SeriesPoint> bookingsSeries,
                                 List<Slice> bookingsByStatus,
                                 List<Slice> earningsByPurpose,
                                 List<HeatCell> demandHeatmap,
                                 List<BookingRow> upcomingBookings) {
    }

    /**
     * The board for whoever is doing the hiring — an organizer or an audience account.
     *
     * @param spend value of bookings raised in the window
     */
    public record BookerOverview(int windowDays,
                                 String fullName,
                                 Metric spend,
                                 Metric bookings,
                                 double lifetimeSpend,
                                 long upcomingCount,
                                 long pendingCount,
                                 List<SeriesPoint> spendSeries,
                                 List<SeriesPoint> bookingsSeries,
                                 List<Slice> bookingsByStatus,
                                 List<Slice> spendByEventType,
                                 List<Slice> paymentsByMethod,
                                 List<TopArtist> favouriteArtists,
                                 List<BookingRow> upcomingBookings,
                                 List<BookingRow> recentBookings) {
    }
}
