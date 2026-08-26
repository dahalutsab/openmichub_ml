package com.brogrammers.open_mic_hub_service.payment.service;

import com.brogrammers.open_mic_hub_service.booking.dto.response.KhaltiInitiateResponse;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.booking.entity.PaymentType;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.mail.MailService;
import com.brogrammers.open_mic_hub_service.payment.dto.PaymentResponse;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.payment.gateway.KhaltiClient;
import com.brogrammers.open_mic_hub_service.payment.gateway.KhaltiLookupResponse;
import com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus;
import com.brogrammers.open_mic_hub_service.payment.repository.PaymentRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.service.TransactionService;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service.VirtualCoinService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentServiceImpl implements PaymentService{
    private final PaymentRepository paymentRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final BookingRepository bookingRepository;
    private final KhaltiClient khaltiClient;

    /** Platform commission taken from each booking. */
    private static final double SYSTEM_FEE_RATE = 0.05;

    @Value("${frontend.domain}")
    private String frontendDomain;
    private final VirtualCoinService virtualCoinService;
    private final TransactionService transactionService;
    private final MailService mailService;
    @Override
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        log.info("Fetching all payments with pagination: {}", pageable);
        return paymentRepository.findAll(pageable)
                .map(PaymentResponse::new);
    }

    @Override
    public Page<PaymentResponse> getAllPaymentsByLoggedInUser(Pageable pageable) {
        log.info("Fetching all payments for logged-in user with pagination: {}", pageable);

        Page<Payment> payments = paymentRepository.findAllByUserInfoEntity(
            loggedInUserUtil.getLoggedInUser(), pageable
        ).orElseThrow(() -> new RuntimeException("No payments found for the logged-in user."));

        return payments.map(PaymentResponse::new);
    }


    @Transactional
    @Override
    public Mono<String> bookArtist(Long bookingId, String paymentType) {
        log.info("Booking payment requested for booking {}", bookingId);

        UserEntity loggedInUser = loggedInUserUtil.getLoggedInUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUserId().getId().equals(loggedInUser.getId())) {
            throw new AccessDeniedException("You can only pay for your own bookings.");
        }

        PaymentType type = parsePaymentType(paymentType);

        // Bill by the minute. toHours() truncated, so a 90-minute booking billed one hour and
        // anything under an hour billed zero and then failed the amount check.
        double hours = Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes() / 60.0;
        if (hours <= 0) {
            throw new IllegalArgumentException("Booking end time must be after its start time.");
        }
        double totalAmount = booking.getArtistId().getHourlyRate() * hours;
        double receivedAmount = type == PaymentType.FULL ? totalAmount : totalAmount / 2;
        if (receivedAmount <= 0) {
            throw new IllegalArgumentException("Payable amount must be greater than zero.");
        }

        Payment payment = new Payment();
        payment.setUserInfoEntity(loggedInUser);
        payment.setBookingId(booking);
        payment.setTotalAmount(totalAmount);
        payment.setPaymentType(type);
        payment.setReceivedAmount(receivedAmount);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setSystemCharges(totalAmount * SYSTEM_FEE_RATE);
        payment.setPaymentMethod("KHALTI");
        payment.setTransactionCode(UUID.randomUUID().toString());
        payment.setProductCode("artist_booking");

        // Persist before calling out. Previously the only save lived inside doOnNext, which runs
        // after the transaction has already committed - if the gateway call failed or nobody
        // subscribed, the payment row was silently never written.
        Payment saved = paymentRepository.save(payment);

        return khaltiClient.initiate(
                bookingId,
                "Booking of Artist",
                BigDecimal.valueOf(receivedAmount),
                frontendDomain + "/user/artist/payment-callback",
                frontendDomain + "/"
        ).map(response -> {
            saved.setPidx(response.getPidx());
            paymentRepository.save(saved);
            log.info("Payment {} initiated with pidx {}", saved.getPaymentId(), response.getPidx());
            return response.getPaymentUrl();
        });
    }

    private PaymentType parsePaymentType(String paymentType) {
        String normalized = paymentType == null ? "" : paymentType.trim().toUpperCase();
        if (normalized.equals(PaymentType.PARTIAL.name())) {
            return PaymentType.PARTIAL;
        }
        if (normalized.equals(PaymentType.FULL.name())) {
            return PaymentType.FULL;
        }
        throw new IllegalArgumentException("Invalid payment type: " + paymentType);
    }

    /**
     * Settles a payment after the customer returns from Khalti.
     *
     * <p>The {@code status} and {@code amount} the browser carries back are advisory only — this
     * endpoint is public, so anything on the query string is attacker-controlled. Settlement is
     * driven entirely by a server-to-server lookup against Khalti, and the captured amount is
     * checked against what we asked for before any money moves.
     *
     * <p>Safe to call repeatedly: a payment that is already settled returns success without
     * crediting the artist a second time.
     */
    @Transactional
    @Override
    public ResponseEntity<String> handleCallback(String pidx) {
        log.info("Processing payment callback for pidx={}", pidx);

        Payment payment = paymentRepository.findByPidx(pidx).orElseThrow(
                () -> new EntityNotFoundException("Payment not found for pidx=" + pidx)
        );

        // Idempotency: a replayed callback must not credit the artist again.
        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment {} already settled; ignoring duplicate callback.", pidx);
            return ResponseEntity.ok("Payment already processed");
        }

        KhaltiLookupResponse verified = khaltiClient.lookup(pidx);
        if (verified == null || !verified.isCompleted()) {
            String reported = verified == null ? "no response" : verified.getStatus();
            log.warn("Khalti reports pidx={} as '{}' - not settling.", pidx, reported);
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            mailService.sendPaymentFailureEmail(payment.getUserInfoEntity(), payment.getBookingId(), payment);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment verification failed");
        }

        // Khalti reports in paisa. Refuse to settle for less than we asked for.
        long expectedPaisa = KhaltiClient.toPaisa(BigDecimal.valueOf(payment.getReceivedAmount()));
        if (verified.getTotalAmount() < expectedPaisa) {
            log.error("Amount mismatch for pidx={}: captured {} paisa, expected {} paisa.",
                    pidx, verified.getTotalAmount(), expectedPaisa);
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment amount mismatch");
        }

        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setReceivedAmount(BigDecimal.valueOf(verified.getTotalAmount())
                .movePointLeft(2).doubleValue());
        payment.setTransactionId(verified.getTransactionId());
        payment.setPaymentTime(LocalTime.now());
        paymentRepository.save(payment);

        Booking booking = payment.getBookingId();
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // Credit the artist. createTransaction is the single place that moves a balance.
        double artistEarnings = artistEarningsFor(payment);
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setBookingId(booking.getId());
        transactionRequest.setArtistId(booking.getArtistId().getId());
        transactionRequest.setAmount(artistEarnings);
        transactionRequest.setTransactionPurpose(TransactionPurpose.BOOKING_PAYMENT);
        transactionRequest.setTransactionType(TransactionType.CREDIT);
        transactionRequest.setStatus(Status.APPROVED);
        transactionService.createTransaction(transactionRequest);

        mailService.sendPaymentConfirmationEmail(payment.getUserInfoEntity(), booking, payment);

        log.info("Payment {} settled; credited {} to artist {}.",
                pidx, artistEarnings, booking.getArtistId().getId());
        return ResponseEntity.ok("Payment processed successfully");
    }

    /** Artist's share of a payment: their portion of the booking value, less the platform fee. */
    private double artistEarningsFor(Payment payment) {
        double gross = payment.getPaymentType() == PaymentType.FULL
                ? payment.getTotalAmount()
                : payment.getTotalAmount() / 2;
        return gross - payment.getSystemCharges();
    }


}
