package com.brogrammers.open_mic_hub_service.booking.controller;

import com.brogrammers.open_mic_hub_service.booking.dto.request.BookingRequest;
import com.brogrammers.open_mic_hub_service.booking.dto.request.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.booking.service.BookingService;
import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequestMapping("/api/v1/artist")
@RestController
@RequiredArgsConstructor
@Slf4j
public class BookingController extends BaseController {
    private final BookingService bookingService;

    @PostMapping("/booking")
    public ResponseEntity<GlobalApiResponse> bookArtist(@RequestBody BookingRequest bookingRequest) {
        log.info("Received booking request: {}", bookingRequest);
        return successResponse(bookingService.bookArtist(bookingRequest), "Artist booked successfully");
    }

    @GetMapping("/getBookings")
    public ResponseEntity<GlobalApiResponse> getAllBookingsOfUsers(Pageable pageable) {
        log.info("Fetching all bookings for users with pageable: {}", pageable);
        return successResponse(bookingService.getAllBookingsOfUsers(pageable), "All bookings fetched successfully");
    }

    @PutMapping("/approve")
    public ResponseEntity<String> approveBooking(@RequestParam Long bookingId) {
        bookingService.approveBooking(bookingId);
        return ResponseEntity.ok("Booking approved successfully");
    }

    @PutMapping("/decline")
    public ResponseEntity<String> declineBooking(@RequestParam Long bookingId) {
        bookingService.declineBooking(bookingId);
        return ResponseEntity.ok("Booking declined successfully");
    }

    @GetMapping("/getAllBooking/user")
    public ResponseEntity<GlobalApiResponse> getAllBookings(Pageable pageable) {
        log.info("Fetching all bookings for artists with pageable: {}", pageable);
        return successResponse(bookingService.getAllBookings(pageable), "All bookings fetched successfully for users");
    }


//    @Operation(
//            summary = "Book the artist by the user for an event",
//            description = "Allows users to book the artist for an event by providing necessary details."
//    )
//    @PostMapping("/booking")
//    public Mono<ResponseEntity<String>> bookArtist(@RequestBody BookingRequest bookingRequest) {
//        return bookingService.bookArtist(bookingRequest)
//                .map(response -> {
//                    log.info("Returning Khalti API response to the user: {}", response);
//                    return ResponseEntity.ok().body(response); // Send the response to the user
//                })
//                .onErrorResume(error -> {
//                    log.error("Error occurred: {}", error.getMessage());
//                    return Mono.just(ResponseEntity.status(500).body(null)); // Handle errors gracefully
//                });
//    }
//
//
//    @Operation(
//            summary = "Handle callback from payment gateway",
//            description = "Processes the callback from the payment gateway after a booking is made."
//    )
//    @GetMapping("/callback")
//    public ResponseEntity<String> handleCallBack(
//            @RequestParam String pidx,
//            @RequestParam String status,
//            @RequestParam double amount,
//            @RequestParam double total_amount) {
//        log.info("Callback received with pidx={}, status={}, amount={}, total_amount={}", pidx, status, amount, total_amount);
//        return bookingService.handleCallback(pidx, status, amount, total_amount);
//    }
//
//    @Operation(
//            summary = "Get all bookings of users",
//            description = "Fetches all bookings made by users, paginated."
//    )
//    @GetMapping("/getAllBookings/users")
//    public ResponseEntity<GlobalApiResponse> getAllBookingsOfUsers(Pageable pageable){
//        return successResponse(bookingService.getAllBookingsOfUsers(pageable), "All bookings fetched successfully");
//    }
//
//    @Operation(
//            summary = "Get all bookings of artists",
//            description = "Fetches all bookings made by artists, paginated."
//    )
//    @GetMapping("/getAllBookings/artists")
//    public ResponseEntity<GlobalApiResponse> getAllBookingsOfArtists(Pageable pageable){
//        return successResponse(bookingService.getAllBookingsOfArtists(pageable), "All bookings fetched successfully for artists");
//    }
//
    @PostMapping("/withdraw")
    public Mono<ResponseEntity<String>> withdraw(@RequestBody WithDrawRequest withDrawRequest) {
        return bookingService.withdraw(withDrawRequest)
                .map(response -> {
                    return ResponseEntity.ok().body(response); // Send the response to the user
                })
                .onErrorResume(error -> {
                    log.error("Error occurred during withdrawal" + error);
                    return Mono.just(ResponseEntity.status(500).body(null)); // Handle errors gracefully
                });
    }

    @PostMapping("/withdraw/callback")
    public ResponseEntity<String> handleWithdrawCallBack(
            @RequestParam String pidx,
            @RequestParam String status,
            @RequestParam double amount) {
        log.info("Callback received with pidx={}, status={}, amount={}", pidx, status, amount);
        return bookingService.handleWithdrawCallBack(pidx, status, amount);
    }

}