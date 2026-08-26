package com.brogrammers.open_mic_hub_service.payment.entity;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.PaymentType;
import com.brogrammers.open_mic_hub_service.common.Auditable;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Payment extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    private LocalTime paymentTime;

    private double receivedAmount;

    private double totalAmount; // Total amount required for the payment

    private double systemCharges;

    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType; // Enum to manage partial or full payment

    private String pidx;

    @Column(name = "transaction_code")
    private String transactionCode;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "product_code")
    private String productCode;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking bookingId;

    @ManyToOne
    private UserEntity userInfoEntity;
}