package com.brogrammers.open_mic_hub_service.analytics.service;

import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.AdminOverview;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.ArtistOverview;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.BookerOverview;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.BookingRow;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.HeatCell;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.Metric;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.SeriesPoint;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.Slice;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.TopArtist;
import com.brogrammers.open_mic_hub_service.analytics.repository.AnalyticsRepository;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Assembles the dashboard payloads.
 *
 * <p>Two conventions run through the whole file:
 *
 * <ul>
 *   <li><b>Every window has a shadow.</b> Each headline figure is computed for the requested window
 *       and again for the equally-long window immediately before it, so the UI can show a delta
 *       without a second round trip.
 *   <li><b>Series are dense.</b> Buckets with no activity are emitted as zero rather than skipped.
 *       A chart drawn from a sparse series silently rescales its axis, which makes a quiet month
 *       look like a busy one.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final VirtualCoinRepository virtualCoinRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    /** Windows shorter than a week have nothing to plot; longer than three years is a report, not a dashboard. */
    private static final int MIN_WINDOW_DAYS = 7;
    private static final int MAX_WINDOW_DAYS = 1095;

    /** Past this many days a daily series is unreadable, so buckets become months. */
    private static final int DAILY_BUCKET_LIMIT = 62;

    private static final int TOP_ARTIST_LIMIT = 6;
    private static final int ROW_LIMIT = 8;

    /** Bookings that represent real, settled money rather than an unanswered request. */
    private static final Set<BookingStatus> SETTLED =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ISO_LOCAL_DATE;

    // ================================================================= admin ==

    @Override
    public AdminOverview adminOverview(int windowDays) {
        Window window = Window.of(windowDays);

        List<Object[]> current = analyticsRepository.findBookingRowsBetween(window.from(), window.to());
        List<Object[]> previous = analyticsRepository.findBookingRowsBetween(window.prevFrom(), window.prevTo());

        double revenueNow = settledValue(current);
        double revenueBefore = settledValue(previous);

        long newUsersNow = analyticsRepository.countUsersRegisteredBetween(window.fromTime(), window.toTime());
        long newUsersBefore = analyticsRepository.countUsersRegisteredBetween(window.prevFromTime(), window.prevToTime());

        double feesNow = analyticsRepository.sumPlatformFeesBetween(window.fromTime(), window.toTime());
        double feesBefore = analyticsRepository.sumPlatformFeesBetween(window.prevFromTime(), window.prevToTime());

        return new AdminOverview(
                window.days(),
                Metric.of(revenueNow, revenueBefore),
                Metric.of(current.size(), previous.size()),
                Metric.of(newUsersNow, newUsersBefore),
                Metric.of(feesNow, feesBefore),
                analyticsRepository.sumPendingWithdrawals(null),
                analyticsRepository.countAllUsers(),
                analyticsRepository.countUsersByRole(UserRole.ARTIST.name()),
                analyticsRepository.countUsersByRole(UserRole.ORGANIZER.name())
                        + analyticsRepository.countUsersByRole(UserRole.USER.name()),
                valueSeries(current, window),
                countSeries(current, window),
                slices(analyticsRepository.countBookingsByStatus(window.from(), window.to())),
                slices(analyticsRepository.countPaymentsByMethod(window.fromTime(), window.toTime())),
                slices(analyticsRepository.countTransactionsByType(window.fromTime(), window.toTime())),
                heatmap(current),
                topArtists(analyticsRepository.findTopArtistsBetween(window.from(), window.to())),
                bookingRows(analyticsRepository.findRecentBookings(window.to()))
        );
    }

    // ================================================================ artist ==

    @Override
    public ArtistOverview artistOverview(int windowDays) {
        Window window = Window.of(windowDays);
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        Long artistId = artist.getId();

        List<Object[]> current =
                analyticsRepository.findArtistBookingRowsBetween(artistId, window.from(), window.to());
        List<Object[]> previous =
                analyticsRepository.findArtistBookingRowsBetween(artistId, window.prevFrom(), window.prevTo());

        List<Object[]> earningRows =
                analyticsRepository.findArtistEarningRowsBetween(artistId, window.fromTime(), window.toTime());
        List<Object[]> previousEarningRows =
                analyticsRepository.findArtistEarningRowsBetween(artistId, window.prevFromTime(), window.prevToTime());

        double earningsNow = sumSecond(earningRows);
        double earningsBefore = sumSecond(previousEarningRows);

        double balance = virtualCoinRepository.findVirtualCoinByArtist(artist)
                .map(VirtualCoin::getBalance)
                .orElse(0d);

        List<Slice> byStatus = slices(
                analyticsRepository.countArtistBookingsByStatus(artistId, window.from(), window.to()));

        UserEntity user = artist.getUser();

        return new ArtistOverview(
                window.days(),
                artist.getStageName(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getProfileImage(),
                artist.getRating(),
                balance,
                analyticsRepository.sumPendingWithdrawals(artistId),
                Metric.of(earningsNow, earningsBefore),
                Metric.of(current.size(), previous.size()),
                analyticsRepository.sumArtistLifetimeEarnings(artistId),
                countStatus(byStatus, BookingStatus.PENDING),
                acceptanceRate(byStatus),
                timestampSeries(earningRows, window),
                countSeries(current, window),
                byStatus,
                slices(analyticsRepository.countArtistTransactionsByPurpose(
                        artistId, window.fromTime(), window.toTime())),
                heatmap(current),
                bookingRows(analyticsRepository.findArtistUpcomingBookings(artistId, LocalDate.now()))
        );
    }

    // ================================================================ booker ==

    @Override
    public BookerOverview bookerOverview(int windowDays) {
        Window window = Window.of(windowDays);
        UserEntity user = loggedInUserUtil.getLoggedInUser();
        Long userId = user.getId();

        List<Object[]> current =
                analyticsRepository.findBookerBookingRowsBetween(userId, window.from(), window.to());
        List<Object[]> previous =
                analyticsRepository.findBookerBookingRowsBetween(userId, window.prevFrom(), window.prevTo());

        List<Slice> byStatus = slices(
                analyticsRepository.countBookerBookingsByStatus(userId, window.from(), window.to()));

        List<Booking> upcoming = analyticsRepository.findBookerUpcomingBookings(userId, LocalDate.now());

        // Lifetime spend covers the whole history, so it is deliberately not window-bounded.
        List<Object[]> lifetime = analyticsRepository.findBookerBookingRowsBetween(
                userId, LocalDate.of(1970, 1, 1), LocalDate.now().plusYears(5));

        return new BookerOverview(
                window.days(),
                user.getFullName(),
                Metric.of(settledValue(current), settledValue(previous)),
                Metric.of(current.size(), previous.size()),
                settledValue(lifetime),
                upcoming.size(),
                countStatus(byStatus, BookingStatus.PENDING),
                valueSeries(current, window),
                countSeries(current, window),
                byStatus,
                slices(analyticsRepository.sumBookerSpendByEventType(userId, window.from(), window.to())),
                slices(analyticsRepository.countBookerPaymentsByMethod(userId, window.fromTime(), window.toTime())),
                topArtists(analyticsRepository.findFavouriteArtists(userId)),
                bookingRows(upcoming),
                bookingRows(analyticsRepository.findBookerPastBookings(userId, LocalDate.now()))
        );
    }

    // =========================================================== bucketing ===

    /**
     * The requested window plus the equally-long one before it.
     *
     * <p>{@code from} and {@code to} are inclusive dates for the booking queries, which key off
     * {@code eventDate}; the {@code *Time} variants are half-open instants for the audit-timestamped
     * queries, so a record written at 23:59 on the last day is not silently dropped.
     */
    private record Window(int days, LocalDate from, LocalDate to) {

        static Window of(int requested) {
            int days = Math.clamp(requested, MIN_WINDOW_DAYS, MAX_WINDOW_DAYS);
            LocalDate to = LocalDate.now();
            return new Window(days, to.minusDays(days - 1L), to);
        }

        LocalDate prevFrom() {
            return from.minusDays(days);
        }

        LocalDate prevTo() {
            return from.minusDays(1);
        }

        LocalDateTime fromTime() {
            return from.atStartOfDay();
        }

        LocalDateTime toTime() {
            return to.plusDays(1).atStartOfDay();
        }

        LocalDateTime prevFromTime() {
            return prevFrom().atStartOfDay();
        }

        LocalDateTime prevToTime() {
            return from.atStartOfDay();
        }

        /** Short windows read best day by day; long ones collapse to months. */
        boolean daily() {
            return days <= DAILY_BUCKET_LIMIT;
        }
    }

    /** Every bucket in the window, pre-seeded to zero so the series is dense. */
    private Map<LocalDate, double[]> emptyBuckets(Window window) {
        Map<LocalDate, double[]> buckets = new TreeMap<>();
        if (window.daily()) {
            for (LocalDate d = window.from(); !d.isAfter(window.to()); d = d.plusDays(1)) {
                buckets.put(d, new double[2]);
            }
        } else {
            LocalDate cursor = window.from().withDayOfMonth(1);
            LocalDate last = window.to().withDayOfMonth(1);
            while (!cursor.isAfter(last)) {
                buckets.put(cursor, new double[2]);
                cursor = cursor.plusMonths(1);
            }
        }
        return buckets;
    }

    private LocalDate bucketKey(LocalDate date, Window window) {
        return window.daily() ? date : date.withDayOfMonth(1);
    }

    private List<SeriesPoint> toPoints(Map<LocalDate, double[]> buckets, Window window) {
        DateTimeFormatter label = window.daily() ? DAY_LABEL : MONTH_LABEL;
        List<SeriesPoint> points = new ArrayList<>(buckets.size());
        buckets.forEach((date, values) ->
                points.add(new SeriesPoint(date.format(label), date, round(values[0]), round(values[1]))));
        return points;
    }

    /**
     * Booking value per bucket: settled money as the primary measure, the full requested value —
     * settled or not — as the secondary, so the gap between the two lines reads as pipeline.
     */
    private List<SeriesPoint> valueSeries(List<Object[]> rows, Window window) {
        Map<LocalDate, double[]> buckets = emptyBuckets(window);
        for (Object[] row : rows) {
            LocalDate key = bucketKey((LocalDate) row[0], window);
            double[] slot = buckets.get(key);
            if (slot == null) {
                continue;
            }
            double amount = toDouble(row[1]);
            if (SETTLED.contains((BookingStatus) row[2])) {
                slot[0] += amount;
            }
            slot[1] += amount;
        }
        return toPoints(buckets, window);
    }

    /** Booking counts per bucket: settled as primary, all requests as secondary. */
    private List<SeriesPoint> countSeries(List<Object[]> rows, Window window) {
        Map<LocalDate, double[]> buckets = emptyBuckets(window);
        for (Object[] row : rows) {
            LocalDate key = bucketKey((LocalDate) row[0], window);
            double[] slot = buckets.get(key);
            if (slot == null) {
                continue;
            }
            if (SETTLED.contains((BookingStatus) row[2])) {
                slot[0] += 1;
            }
            slot[1] += 1;
        }
        return toPoints(buckets, window);
    }

    /** Series for rows keyed by an audit timestamp rather than an event date. */
    private List<SeriesPoint> timestampSeries(List<Object[]> rows, Window window) {
        Map<LocalDate, double[]> buckets = emptyBuckets(window);
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            LocalDate key = bucketKey(((LocalDateTime) row[0]).toLocalDate(), window);
            double[] slot = buckets.get(key);
            if (slot == null) {
                continue;
            }
            slot[0] += toDouble(row[1]);
            slot[1] += 1;
        }
        return toPoints(buckets, window);
    }

    /**
     * The week/hour grid, from each booking's start time.
     *
     * <p>Only cells with activity are emitted — the client draws the full 7×24 lattice and looks
     * cells up, so sending 168 mostly-zero rows would be wasted payload.
     */
    private List<HeatCell> heatmap(List<Object[]> rows) {
        Map<Integer, long[]> grid = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            LocalTime start = (LocalTime) row[3];
            if (date == null || start == null) {
                continue;
            }
            int dow = date.getDayOfWeek().getValue();
            int hour = start.getHour();
            grid.computeIfAbsent(dow * 100 + hour, k -> new long[]{dow, hour, 0})[2]++;
        }
        return grid.values().stream()
                .map(cell -> new HeatCell((int) cell[0], (int) cell[1], cell[2]))
                .toList();
    }

    // =============================================================== mapping ==

    /** Maps {@code (label, count, amount)} triples from a GROUP BY into slices. */
    private List<Slice> slices(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Slice(
                        row[0] == null ? "UNKNOWN" : row[0].toString(),
                        row[1] == null ? 0L : ((Number) row[1]).longValue(),
                        round(toDouble(row[2]))))
                .sorted(Comparator.comparingLong(Slice::count).reversed())
                .toList();
    }

    private List<TopArtist> topArtists(List<Object[]> rows) {
        return rows.stream()
                .limit(TOP_ARTIST_LIMIT)
                .map(row -> new TopArtist(
                        row[0] == null ? null : ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        row[5] == null ? 0L : ((Number) row[5]).longValue(),
                        round(toDouble(row[6])),
                        row[4] == null ? null : ((Number) row[4]).doubleValue()))
                .toList();
    }

    private List<BookingRow> bookingRows(List<Booking> bookings) {
        return bookings.stream()
                .limit(ROW_LIMIT)
                .map(b -> new BookingRow(
                        b.getId(),
                        b.getEventDate(),
                        b.getStartTime() == null ? null : b.getStartTime().toString(),
                        b.getEndTime() == null ? null : b.getEndTime().toString(),
                        b.getVenue(),
                        b.getEventType(),
                        b.getStatus() == null ? null : b.getStatus().name(),
                        round(b.getTotalAmount()),
                        b.getArtistId() == null ? null : b.getArtistId().getStageName(),
                        b.getUserId() == null ? null : b.getUserId().getFullName()))
                .toList();
    }

    // ================================================================= maths ==

    private double settledValue(List<Object[]> rows) {
        double total = 0d;
        for (Object[] row : rows) {
            if (SETTLED.contains((BookingStatus) row[2])) {
                total += toDouble(row[1]);
            }
        }
        return round(total);
    }

    private double sumSecond(List<Object[]> rows) {
        double total = 0d;
        for (Object[] row : rows) {
            total += toDouble(row[1]);
        }
        return round(total);
    }

    private long countStatus(List<Slice> slices, BookingStatus status) {
        return slices.stream()
                .filter(s -> status.name().equals(s.label()))
                .mapToLong(Slice::count)
                .sum();
    }

    /**
     * Share of decided requests that were accepted.
     *
     * <p>Still-pending requests are excluded from both halves: counting them as rejections would
     * punish an artist for requests they have not had a chance to answer yet.
     */
    private double acceptanceRate(List<Slice> slices) {
        long accepted = 0;
        long decided = 0;
        for (Slice slice : slices) {
            if (BookingStatus.PENDING.name().equals(slice.label())) {
                continue;
            }
            decided += slice.count();
            if (BookingStatus.CONFIRMED.name().equals(slice.label())
                    || BookingStatus.COMPLETED.name().equals(slice.label())) {
                accepted += slice.count();
            }
        }
        return decided == 0 ? 0d : Math.round((accepted * 1000d / decided)) / 10d;
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    /** Money to two places. Charts and totals both read these, so rounding happens once, here. */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
