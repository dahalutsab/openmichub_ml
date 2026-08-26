package com.brogrammers.open_mic_hub_service.booking.entity;

import com.brogrammers.open_mic_hub_service.common.Auditable;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Booking extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;


    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private String eventType;

    private double totalAmount;

//    @Enumerated(EnumType.STRING)
//    private PaymentType paymentType;

//    @Column(nullable = false)
//    private String paymentGateway;

//    @Enumerated(EnumType.STRING)
//    private RemainingPaymentMethod remainingPaymentMethod;

//    private double remainingAmount;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity userId;

    @ManyToOne
    @JoinColumn(name = "artist_id")
    private Artist artistId;

//    @OneToMany(mappedBy = "bookingId", cascade = CascadeType.ALL)
//    private List<Payment> payments;

//    private double systemCharges;
}
