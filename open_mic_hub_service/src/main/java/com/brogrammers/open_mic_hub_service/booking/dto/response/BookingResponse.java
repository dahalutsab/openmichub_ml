package com.brogrammers.open_mic_hub_service.booking.dto.response;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String bookingStatus;
    private String bookingType;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
    private double price;
    private ArtistResponses artistResponse;
    private boolean isPaymentDone;

    public BookingResponse(Booking savedBooking) {
        this.bookingId = savedBooking.getId();
        this.bookingStatus = savedBooking.getStatus().name();
        this.bookingType = savedBooking.getEventType();
        this.bookingDate = savedBooking.getEventDate();
        this.startTime = savedBooking.getStartTime();
        this.endTime = savedBooking.getEndTime();
        this.venue = savedBooking.getVenue();
        this.price = savedBooking.getTotalAmount();
        this.artistResponse = new ArtistResponses(savedBooking.getArtistId());
    }
}
