package com.brogrammers.open_mic_hub_service.booking;

import com.brogrammers.open_mic_hub_service.booking.dto.request.BookingRequest;
import com.brogrammers.open_mic_hub_service.booking.dto.response.BookingResponse;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.booking.service.BookingServiceImpl;
import com.brogrammers.open_mic_hub_service.mail.MailService;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.AvailabilityTime;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository.ArtistAvailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.repository.ArtistUnavailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers booking an artist: what is refused, and what a slot costs.
 *
 * <p>None of this was covered before, and all of it was broken. The endpoint threw on every
 * request because the availability collection was read outside a session; behind that, the price
 * was computed with integer hour truncation, so a half-hour booking cost nothing and a slot that
 * ran backwards produced a negative total. The seeded bookings were inserted in SQL and never
 * exercised this path, which is exactly why it needs tests rather than sample data.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceImplTest {

    private static final long ARTIST_ID = 2L;
    private static final double HOURLY_RATE = 3150.0;

    @Mock private BookingRepository bookingRepository;
    @Mock private LoggedInUserUtil loggedInUserUtil;
    @Mock private ArtistRepository artistRepository;
    @Mock private ArtistAvailabilityRepository artistAvailabilityRepository;
    @Mock private ArtistUnavailabilityRepository artistUnavailabilityRepository;
    @Mock private MailService mailService;

    @InjectMocks private BookingServiceImpl bookingService;

    private Artist artist;
    private LocalDate nextThursday;

    @BeforeEach
    void setUp() {
        UserEntity owner = new UserEntity();
        owner.setFullName("Manisha Limbu");

        artist = new Artist();
        artist.setId(ARTIST_ID);
        artist.setStageName("Manisha Limbu");
        artist.setHourlyRate(HOURLY_RATE);
        artist.setUser(owner);

        // Computed rather than hard-coded: a fixed date would drift into the past and start
        // failing on the "no bookings in the past" rule for reasons unrelated to the test.
        nextThursday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.THURSDAY));

        AvailabilityTime evening = new AvailabilityTime();
        evening.setStartTime(LocalTime.of(20, 0));
        evening.setEndTime(LocalTime.of(23, 0));

        ArtistAvailability thursday = new ArtistAvailability();
        thursday.setArtist(artist);
        thursday.setDayOfWeek(DayOfWeek.THURSDAY);
        thursday.setAvailabilityTimes(List.of(evening));

        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        when(artistAvailabilityRepository.findByArtistAndDayOfWeek(eq(artist), any()))
                .thenReturn(Optional.of(thursday));
        when(artistUnavailabilityRepository.existsByArtistAndDate(eq(artist), any())).thenReturn(false);
        when(bookingRepository.existsOverlapping(eq(artist), any(), any(), any(), anyList()))
                .thenReturn(false);
        when(loggedInUserUtil.getLoggedInUser()).thenReturn(new UserEntity());
        // Saving hands back what it was given, with an id, so assertions can read the priced row.
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(1L);
            return booking;
        });
    }

    private BookingRequest request(LocalDate date, LocalTime start, LocalTime end) {
        BookingRequest request = new BookingRequest();
        request.setArtistId(ARTIST_ID);
        request.setEventDate(date);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setVenue("Patan Durbar Square");
        request.setEventType("Corporate");
        return request;
    }

    private BookingRequest slot(LocalTime start, LocalTime end) {
        return request(nextThursday, start, end);
    }

    @Nested
    @DisplayName("what a slot costs")
    class Pricing {

        @Test
        @DisplayName("a whole number of hours is the rate times the hours")
        void pricesWholeHours() {
            BookingResponse response = bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(22, 0)));

            assertThat(response.getPrice()).isEqualTo(6300.0);
        }

        @Test
        @DisplayName("ninety minutes is charged as an hour and a half, not as one hour")
        void pricesPartHours() {
            // Duration.toHours() truncated this to 1, so the artist lost half the fee on every
            // booking that did not land on the hour.
            BookingResponse response = bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(21, 30)));

            assertThat(response.getPrice()).isEqualTo(4725.0);
        }

        @Test
        @DisplayName("a half-hour booking costs something")
        void pricesSubHourSlots() {
            // The truncation made this exactly zero: a free booking, bookable by anyone.
            BookingResponse response = bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(20, 30)));

            assertThat(response.getPrice()).isEqualTo(1575.0);
        }

        @Test
        @DisplayName("the total is rounded to the paisa")
        void roundsToTwoDecimals() {
            // 20 minutes of 3150 is 1049.999...; money should not carry a repeating decimal.
            BookingResponse response = bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(20, 20)));

            assertThat(response.getPrice()).isEqualTo(1050.0);
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("a slot that ends before it starts")
        void rejectsReversedTimes() {
            // This one passed every other check — it sits inside the availability window and
            // overlaps nothing — and then priced at a negative number of hours, crediting the
            // organizer instead of charging them.
            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(22, 0), LocalTime.of(21, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("end time must be after the start time");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a slot of no length at all")
        void rejectsZeroLengthSlot() {
            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(21, 0), LocalTime.of(21, 0))))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a date that has already passed")
        void rejectsPastDates() {
            LocalDate lastThursday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.THURSDAY));

            assertThatThrownBy(() -> bookingService.bookArtist(
                    request(lastThursday, LocalTime.of(20, 0), LocalTime.of(22, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be in the past");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a time outside the artist's window for that day")
        void rejectsTimesOutsideAvailability() {
            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(18, 0), LocalTime.of(19, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not available at the requested time");
        }

        @Test
        @DisplayName("a slot that runs past the end of the window")
        void rejectsSlotsOverrunningTheWindow() {
            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(22, 0), LocalTime.of(23, 30))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not available at the requested time");
        }

        @Test
        @DisplayName("a day the artist has not published any availability for")
        void rejectsUnavailableDay() {
            when(artistAvailabilityRepository.findByArtistAndDayOfWeek(eq(artist), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(22, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not available on the requested day");
        }

        @Test
        @DisplayName("a date the artist has blocked out")
        void rejectsBlackoutDates() {
            when(artistUnavailabilityRepository.existsByArtistAndDate(eq(artist), any())).thenReturn(true);

            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(22, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unavailable on");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a slot someone else already holds")
        void rejectsOverlappingBookings() {
            when(bookingRepository.existsOverlapping(eq(artist), any(), any(), any(), anyList()))
                    .thenReturn(true);

            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(22, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already booked");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("an artist that does not exist")
        void rejectsUnknownArtist() {
            when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(22, 0))))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("the row it writes")
    class Persistence {

        @Test
        @DisplayName("starts life pending, with the requested slot on it")
        void savesAPendingBooking() {
            BookingResponse response = bookingService.bookArtist(
                    slot(LocalTime.of(20, 0), LocalTime.of(22, 0)));

            assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.PENDING.name());
            assertThat(response.getBookingDate()).isEqualTo(nextThursday);
            assertThat(response.getStartTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(response.getEndTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(response.getVenue()).isEqualTo("Patan Durbar Square");
        }

        @Test
        @DisplayName("tells the artist about it")
        void notifiesTheArtist() {
            bookingService.bookArtist(slot(LocalTime.of(20, 0), LocalTime.of(22, 0)));

            verify(mailService).sendBookingRequestEmail(eq(artist), any(Booking.class));
        }

        @Test
        @DisplayName("checks the overlap against live bookings only")
        void checksOverlapAgainstLiveStatusesOnly() {
            // A declined or cancelled booking must not hold the slot; only PENDING and CONFIRMED
            // represent a claim on the artist's evening.
            bookingService.bookArtist(slot(LocalTime.of(20, 0), LocalTime.of(22, 0)));

            verify(bookingRepository).existsOverlapping(
                    eq(artist), eq(nextThursday), eq(LocalTime.of(20, 0)), eq(LocalTime.of(22, 0)),
                    eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)));
        }
    }
}
