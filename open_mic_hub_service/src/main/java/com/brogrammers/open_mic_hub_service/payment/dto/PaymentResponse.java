package com.brogrammers.open_mic_hub_service.payment.dto;

import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
public class PaymentResponse {
    private LocalTime paymentTime;
    private double receivedAmount;
    private String paymentMethod;
    private PaymentStatus paymentStatus;
    private String productCode;
    private PaymentBookingResponse booking;

    public PaymentResponse(Payment payment) {
        this.paymentTime = payment.getPaymentTime();
        this.receivedAmount = payment.getReceivedAmount();
        this.paymentMethod = payment.getPaymentMethod();
        this.paymentStatus = payment.getPaymentStatus();
        this.productCode = payment.getProductCode();
        if (payment.getBookingId() != null) {
            this.booking = new PaymentBookingResponse(payment);
        } else {
            this.booking = null; // Handle the case where bookingId is null
        }
    }
}
