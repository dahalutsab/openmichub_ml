package com.brogrammers.open_mic_hub_service.booking.service;

import com.brogrammers.open_mic_hub_service.booking.dto.request.BookingRequest;
import com.brogrammers.open_mic_hub_service.booking.dto.request.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.booking.dto.response.BookingResponse;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import reactor.core.publisher.Mono;

public interface BookingService {
    BookingResponse bookArtist(BookingRequest bookingRequest);

    Page<BookingResponse> getAllBookingsOfUsers(Pageable pageable);

    void approveBooking(Long bookingId);

    void declineBooking(Long bookingId);

    Page<BookingResponse> getAllBookings(Pageable pageable);

//    Mono<String> bookArtist(BookingRequest bookingRequest);
//
//
//    ResponseEntity<String> handleCallback(String pidx, String status, double amount, double totalAmount);
//
//    Page<BookingResponse> getAllBookingsOfUsers(Pageable pageable);
//
//    Page<ArtistBookingResponse> getAllBookingsOfArtists(Pageable pageable);
//
    Mono<String> withdraw(WithDrawRequest withDrawRequest);

        ResponseEntity<String> handleWithdrawCallBack(String pidx, String status, double amount);

}
