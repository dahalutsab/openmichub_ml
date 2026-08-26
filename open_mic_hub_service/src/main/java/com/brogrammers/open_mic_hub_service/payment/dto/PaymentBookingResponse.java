package com.brogrammers.open_mic_hub_service.payment.dto;

import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class PaymentBookingResponse {
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BookingStatus status;
    private String venue;
    private String eventType;
    private double totalAmount;

    public PaymentBookingResponse(Payment payment){
        this.eventDate = payment.getBookingId().getEventDate();
        this.startTime = payment.getBookingId().getStartTime();
        this.endTime = payment.getBookingId().getEndTime();
        this.status = payment.getBookingId().getStatus();
        this.venue = payment.getBookingId().getVenue();
        this.eventType = payment.getBookingId().getEventType();
        this.totalAmount = payment.getBookingId().getTotalAmount();
    }
}
