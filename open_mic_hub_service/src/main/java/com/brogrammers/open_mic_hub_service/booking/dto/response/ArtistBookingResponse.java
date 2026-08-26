package com.brogrammers.open_mic_hub_service.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtistBookingResponse {
    private Long bookingId;
    private double totalAmount;
    private double receivedAmount;
    private double remainingAmount;
    private String bookingStatus;
    private double systemCharges;
    private String venue;
    private LocalDate bookingDate;
    private UserResponse userResponse;

    public ArtistBookingResponse(Long id, double totalAmount, double sum, double remainingAmount, double systemCharges, String name, String venue, LocalDate eventDate, UserResponse userResponse) {
        this.bookingId = id;
        this.totalAmount = totalAmount;
        this.receivedAmount = sum;
        this.remainingAmount = remainingAmount;
        this.systemCharges = systemCharges;
        this.venue = venue;
        this.bookingDate = eventDate;
        this.userResponse = userResponse;
    }
}
