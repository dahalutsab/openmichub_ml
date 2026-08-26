package com.brogrammers.open_mic_hub_service.payment.service;

import com.brogrammers.open_mic_hub_service.booking.dto.response.KhaltiInitiateResponse;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.booking.entity.PaymentType;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.mail.MailService;
import com.brogrammers.open_mic_hub_service.payment.dto.PaymentResponse;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    private final WebClient.Builder webClientBuilder;
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


    @Value("${khalti.secret-key}")
    private String khaltiSecretKey;

    @Value("${khalti.base-url}")
    private String khaltiBaseUrl;

    @Transactional
    @Override
    public Mono<String> bookArtist(Long bookingId, String paymentType) {
        log.info("Booking request received: {}", bookingId);

        // Fetch the logged-in user
        UserEntity loggedInUser = loggedInUserUtil.getLoggedInUser();

        Payment payment = new Payment();
        payment.setUserInfoEntity(loggedInUser);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + bookingId));
        payment.setBookingId(booking);

        double artistRate = booking.getArtistId().getHourlyRate();
        long durationInHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        double totalAmount = artistRate * durationInHours;
        payment.setTotalAmount(totalAmount);

        // Convert paymentType to uppercase and validate
        String paymentTypeUpper = paymentType != null ? paymentType.toUpperCase() : "";
        double receivedAmount = 0.0;

        if (paymentTypeUpper.equals(PaymentType.PARTIAL.name())) {
            payment.setPaymentType(PaymentType.PARTIAL);
            receivedAmount = totalAmount / 2;
        } else if (paymentTypeUpper.equals(PaymentType.FULL.name())) {
            payment.setPaymentType(PaymentType.FULL);
            receivedAmount = totalAmount;
        } else {
            throw new IllegalArgumentException("Invalid payment type: " + paymentType);
        }

        // Validate receivedAmount
        if (receivedAmount <= 0) {
            throw new IllegalArgumentException("Received amount must be greater than zero.");
        }

        payment.setReceivedAmount(receivedAmount);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        double systemCharges = totalAmount * 0.05; // Assuming 5% system charges
        payment.setSystemCharges(systemCharges);
        payment.setPaymentMethod("KHALTI");
        payment.setTransactionCode(UUID.randomUUID().toString());
        payment.setProductCode("artist_booking");
        payment.setUserInfoEntity(loggedInUser);

        // Call the helper method for Khalti payment initiation
        return initiateKhaltiPayment(
                bookingId,
                "Booking of Artist",
                payment.getReceivedAmount(),
                "http://localhost:4200/user/artist/payment-callback",
                "http://localhost:4200/"
        ).doOnNext(paymentUrl -> {
            String pidx = extractPidxFromUrl(paymentUrl); // Extract pidx from the URL
            payment.setPidx(pidx); // Save the extracted pidx
            paymentRepository.save(payment);
            log.info("Updated payment with pidx: {}", payment.getPidx());
        });
    }

    private Mono<String> initiateKhaltiPayment(Long purchaseOrderId, String purchaseOrderName, double amount, String returnUrl, String websiteUrl) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        log.info("Initiating Khalti payment for purchaseOrderId={}, purchaseOrderName={}, amount={}", purchaseOrderId, purchaseOrderName, amount);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("return_url", returnUrl);
        requestBody.put("website_url", websiteUrl);
        requestBody.put("amount", (int) (amount * 100)); // in paisa
        requestBody.put("purchase_order_id", purchaseOrderId);
        requestBody.put("purchase_order_name", purchaseOrderName);

        log.info("Request body for Khalti payment: {}", requestBody);

        return webClientBuilder.build()
                .post()
                .uri(khaltiBaseUrl + "/epayment/initiate/")
                .header(HttpHeaders.AUTHORIZATION, "Key " + khaltiSecretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(KhaltiInitiateResponse.class)
                .doOnNext(response -> log.info("Khalti API response: {}", response))
                .flatMap(khaltiResponse ->
                        Mono.fromCallable(() -> {
                            String paymentUrl = khaltiResponse.getPaymentUrl();
                            log.info("Payment URL received: {}", paymentUrl);

                            String pidx = extractPidxFromUrl(paymentUrl);
                            log.info("Extracted pidx: {}", pidx);

                            // Save payment with pidx here if needed

                            return paymentUrl; // ✅ Return the actual payment URL
                        }).subscribeOn(Schedulers.boundedElastic())
                )
                .doOnError(error -> log.error("Error in Khalti API call: ", error));
    }


    private String extractPidxFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String query = uri.getQuery(); // Extract query parameters
            if (query == null) {
                throw new IllegalArgumentException("Query string is null in the URL");
            }
            String[] params = query.split("&"); // Split by '&'
            for (String param : params) {
                if (param.startsWith("pidx=")) {
                    return param.split("=")[1]; // Return the value of pidx
                }
            }
        } catch (Exception e) {
            log.error("Error extracting pidx from URL: {}", e.getMessage());
        }
        throw new IllegalArgumentException("pidx not found in the URL");
    }

    @Transactional
    @Override
    public ResponseEntity<String> handleCallback(String pidx, String status, double amount, double totalAmount) {
        try {
            log.info("Processing callback with pidx={}, status={}, amount={}, totalAmount={}", pidx, status, amount, totalAmount);

            Payment payment = paymentRepository.findByPidx(pidx).orElseThrow(
                    () -> new EntityNotFoundException("Payment not found for pidx=" + pidx)
            );


            if ("Completed".equalsIgnoreCase(status)) {
                log.info("Payment verified successfully for pidx={}", pidx);
                payment.setPaymentStatus(PaymentStatus.COMPLETED);
                payment.setReceivedAmount(amount / 100);
               payment.setPaymentTime(LocalTime.from(LocalDateTime.now()));
               paymentRepository.save(payment);



                VirtualCoinRequest virtualCoinRequest = new VirtualCoinRequest();
                virtualCoinRequest.setArtistId(payment.getBookingId().getArtistId().getId());
                if (payment.getPaymentType() == PaymentType.FULL) {
                    virtualCoinRequest.setBalance(payment.getTotalAmount() - payment.getSystemCharges());
                } else {
                    virtualCoinRequest.setBalance(payment.getTotalAmount() / 2 - payment.getSystemCharges());
                }

                // Creating or Updating virtual coin
                virtualCoinService.createOrUpdateVirtualCoin(virtualCoinRequest);

                TransactionRequest transactionRequest = new TransactionRequest();
                transactionRequest.setBookingId(payment.getBookingId().getId());
                transactionRequest.setArtistId(payment.getBookingId().getArtistId().getId());
                if (payment.getPaymentType() == PaymentType.FULL) {
                    transactionRequest.setAmount(payment.getTotalAmount() - payment.getSystemCharges());
                } else {
                    transactionRequest.setAmount(payment.getTotalAmount() / 2 - payment.getSystemCharges());
                }
                transactionRequest.setTransactionPurpose(TransactionPurpose.BOOKING_PAYMENT);
                transactionRequest.setTransactionType(TransactionType.CREDIT);
                transactionRequest.setStatus(Status.APPROVED);
                log.info("TransactionRequest before saving: {}", transactionRequest);
                transactionService.createTransaction(transactionRequest);

                // Send confirmation email
                UserEntity user = payment.getUserInfoEntity();
                mailService.sendPaymentConfirmationEmail(user, payment.getBookingId(), payment);

                log.info("Payment and booking status updated successfully.");
                return ResponseEntity.ok("Payment processed successfully");
            } else {
                log.info("Payment verification failed for pidx={}", pidx);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);

//                Booking booking = bookingRepository.findById(payment.getBookingId().getId())
//                        .orElseThrow(() -> new EntityNotFoundException("Booking not found for id=" + payment.getBookingId().getId()));
//
//                booking.setStatus(BookingStatus.CANCELLED);
//                bookingRepository.save(booking);

                // Send failure email
                UserEntity user = payment.getUserInfoEntity();
                mailService.sendPaymentFailureEmail(user, payment.getBookingId(), payment);

                log.info("Payment failed and booking status updated to CANCELLED.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment verification failed");
            }
        } catch (Exception e) {
            log.error("Error occurred while processing callback: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error occurred");
        }
    }


}
