package com.brogrammers.open_mic_hub_service.booking.service;

import com.brogrammers.open_mic_hub_service.booking.dto.request.BookingRequest;
import com.brogrammers.open_mic_hub_service.booking.dto.request.WithDrawRequest;
import com.brogrammers.open_mic_hub_service.booking.dto.response.BookingResponse;
import com.brogrammers.open_mic_hub_service.booking.dto.response.KhaltiInitiateResponse;
import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.booking.entity.BookingStatus;
import com.brogrammers.open_mic_hub_service.booking.entity.PaymentType;
import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.mail.MailService;
import com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus;
import com.brogrammers.open_mic_hub_service.payment.repository.PaymentRepository;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus;
import com.brogrammers.open_mic_hub_service.payment.repository.PaymentRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository.ArtistAvailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.dto.TransactionRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Status;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.Transaction;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionPurpose;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.entity.TransactionType;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.repository.TransactionRepository;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.transaction.service.TransactionService;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.dto.VirtualCoinRequest;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.service.VirtualCoinService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final ArtistRepository artistRepository;
    private final ArtistAvailabilityRepository artistAvailabilityRepository;
    private final MailService mailService;
    private final WebClient.Builder webClientBuilder;
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final VirtualCoinService virtualCoinService;
    private final VirtualCoinRepository virtualCoinRepository;
    private final TransactionService transactionService;

    public static class TimeFormatConverter {
        public static LocalTime convertToLocalTime(String time) {
            try {
                // Try parsing with AM/PM format
                DateTimeFormatter amPmFormatter = DateTimeFormatter.ofPattern("h:mm a");
                return LocalTime.parse(time, amPmFormatter);
            } catch (DateTimeParseException e1) {
                try {
                    // Fallback to 24-hour format
                    DateTimeFormatter twentyFourHourFormatter = DateTimeFormatter.ofPattern("HH:mm");
                    return LocalTime.parse(time, twentyFourHourFormatter);
                } catch (DateTimeParseException e2) {
                    throw new IllegalArgumentException("Invalid time format: " + time, e2);
                }
            }
        }
    }

    @Override
    public BookingResponse bookArtist(BookingRequest bookingRequest) {
        BookingServiceImpl.log.info("Booking request received: {}", bookingRequest);

        // Convert startTime and endTime to LocalTime
        LocalTime startTime = TimeFormatConverter.convertToLocalTime(String.valueOf(bookingRequest.getStartTime()));
        LocalTime endTime = TimeFormatConverter.convertToLocalTime(String.valueOf(bookingRequest.getEndTime()));

        // Fetch the artist by ID
        Artist artist = artistRepository.findById(bookingRequest.getArtistId())
                .orElseThrow(() -> new EntityNotFoundException("Artist not found with ID: " + bookingRequest.getArtistId()));

        // Validate artist availability
        DayOfWeek requestedDay = bookingRequest.getEventDate().getDayOfWeek();
        ArtistAvailability artistAvailability = artistAvailabilityRepository.findByArtistAndDayOfWeek(artist, requestedDay)
                .orElseThrow(() -> new IllegalArgumentException("Artist is not available on the requested day: " + requestedDay));
        boolean isAvailable = artistAvailability.getAvailabilityTimes().stream().anyMatch(availabilityTime -> {
            BookingServiceImpl.log.info("Checking availability: Artist available from {} to {}, Requested from {} to {}",
                    availabilityTime.getStartTime(), availabilityTime.getEndTime(),
                    startTime, endTime);

            boolean isWithinAvailability = !startTime.isBefore(availabilityTime.getStartTime()) &&
                    !endTime.isAfter(availabilityTime.getEndTime());

            BookingServiceImpl.log.info("Is requested time within availability? {}", isWithinAvailability);
            return isWithinAvailability;
        });

        if (!isAvailable) {
            BookingServiceImpl.log.error("Artist is not available at the requested time: {} to {}", startTime, endTime);
            throw new IllegalArgumentException("Artist is not available at the requested time.");
        }
        // Create and save the booking entity
        Booking booking = new Booking();
        booking.setArtistId(artist);
        booking.setEventDate(bookingRequest.getEventDate());
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setVenue(bookingRequest.getVenue());
        booking.setEventType(bookingRequest.getEventType());
        booking.setStatus(BookingStatus.PENDING); // Set initial status as PENDING
        booking.setUserId(loggedInUserUtil.getLoggedInUser());
        // Calculate the duration in hours between startTime and endTime
        long durationInHours = java.time.Duration.between(startTime, endTime).toHours();
        booking.setTotalAmount(artist.getHourlyRate() * durationInHours);
        Booking savedBooking = bookingRepository.save(booking);

        BookingServiceImpl.log.info("Booking successfully created: {}", booking);
        // Send email to the artist
        mailService.sendBookingRequestEmail(artist, savedBooking);

        return new BookingResponse(savedBooking);
    }

    @Override
    public Page<BookingResponse> getAllBookingsOfUsers(Pageable pageable) {
        BookingServiceImpl.log.info("Fetching all bookings for the logged-in user");
        // Fetch the logged-in user
        Artist loggedInUser = loggedInUserUtil.getLoggedInArtist();

        Page<Booking> booking = bookingRepository.findAllByArtistIdAndStatus(loggedInUser, BookingStatus.PENDING, pageable);
        if (booking != null && booking.hasContent()) {
            return booking.map(BookingResponse::new);
        }
        BookingServiceImpl.log.warn("No bookings found for the logged-in user");
        return Page.empty(pageable);
    }

    @Override
    public void approveBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        Artist artist = booking.getArtistId();
        UserEntity user = booking.getUserId();

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        BookingServiceImpl.log.info("Booking approved: {}", booking);

        // Send approval email
        mailService.sendBookingApprovalEmail(user, artist, booking);
    }

    @Override
    public void declineBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        UserEntity user = booking.getUserId();

        booking.setStatus(BookingStatus.DECLINED);
        bookingRepository.save(booking);

        BookingServiceImpl.log.info("Booking declined: {}", booking);

        // Send decline email
        mailService.sendBookingDeclineEmail(user, booking);
    }

//    @Override
//    public Page<BookingResponse> getAllBookings(Pageable pageable) {
//        log.info("Fetching all bookings for the logged-in artist");
//        UserEntity loggedInArtist = loggedInUserUtil.getLoggedInUser();
//        Page<Booking> bookings = bookingRepository.findAllByUserId(loggedInArtist, pageable);
//        if (bookings != null && bookings.hasContent()) {
//            return bookings.map(BookingResponse::new);
//        }
//        log.warn("No bookings found for the logged-in artist");
//        return Page.empty(pageable);
//    }

    @Override
    public Page<BookingResponse> getAllBookings(Pageable pageable) {
        BookingServiceImpl.log.info("Fetching all bookings for the logged-in artist");
        UserEntity loggedInArtist = loggedInUserUtil.getLoggedInUser();
        Page<Booking> bookings = bookingRepository.findAllByUserId(loggedInArtist, pageable);
        if (bookings != null && bookings.hasContent()) {
            return bookings.map(booking -> {
                boolean isPaymentDone = paymentRepository
                        .findByBookingId(booking)
                        .stream()
                        .anyMatch(payment -> payment.getPaymentStatus() == PaymentStatus.COMPLETED);

                BookingResponse response = new BookingResponse(booking);
                response.setPaymentDone(isPaymentDone);
                return response;
            });
        }
        BookingServiceImpl.log.warn("No bookings found for the logged-in artist");
        return Page.empty(pageable);
    }


    @Value("${khalti.secret-key}")
    private String khaltiSecretKey;

    @Value("${khalti.base-url}")
    private String khaltiBaseUrl;

    //
//    @Transactional
//    @Override
//    public Mono<String> bookArtist(BookingRequest bookingRequest) {
//        log.info("Booking request received: {}", bookingRequest);
//
//        // Fetch the logged-in user
//        UserEntity loggedInUser = loggedInUserUtil.getLoggedInUser();
//
//        // Fetch the artist by ID
//        Artist artist = artistRepository.findById(bookingRequest.getArtistId())
//                .orElseThrow(() -> new EntityNotFoundException("Artist not found with ID: " + bookingRequest.getArtistId()));
//
//        // Create and save Booking entity
//        Booking booking = new Booking();
//        booking.setVenue(bookingRequest.getVenue());
//        booking.setEventDate(bookingRequest.getEventDate());
//        booking.setStartTime(bookingRequest.getStartTime());
//        booking.setEndTime(bookingRequest.getEndTime());
//        booking.setEventType(bookingRequest.getEventType());
//        booking.setPaymentType(bookingRequest.getPaymentType());
//        booking.setPaymentGateway(bookingRequest.getPaymentGateway());
//        booking.setArtistId(artist);
//        booking.setUserId(loggedInUser);
//
//        double artistRate = artist.getHourlyRate();
//        long durationInHours = java.time.Duration.between(bookingRequest.getStartTime(), bookingRequest.getEndTime()).toHours();
//        double totalAmount = artistRate * durationInHours;
//        booking.setTotalAmount(totalAmount);
//        if (bookingRequest.getPaymentType().equals(PaymentType.PARTIAL)) {
//            booking.setFullyPaid(false);
//            double paidAmount = totalAmount / 2;
//            double remainingAmount = totalAmount - paidAmount;
//            booking.setRemainingAmount(remainingAmount);
//        } else {
//            booking.setFullyPaid(true);
//        }
//        booking.setStatus(BookingStatus.PENDING);
//        booking.setSystemCharges(totalAmount * 0.05); // Assuming 5% system charges
//        Booking savedBooking = bookingRepository.save(booking);
//
//        // Create and save Payment entity
//        Payment payment = new Payment();
//        payment.setReceivedAmount(bookingRequest.getPaymentType().equals(PaymentType.PARTIAL) ? totalAmount / 2 : totalAmount);
//        payment.setPaymentMethod(bookingRequest.getPaymentGateway());
//        payment.setPaymentStatus(PaymentStatus.PENDING);
//        payment.setTransactionCode(UUID.randomUUID().toString());
//        payment.setProductCode("product_code_placeholder");
//        payment.setBookingId(savedBooking);
//
//        // Call the helper method for Khalti payment initiation
//        return initiateKhaltiPayment(
//                savedBooking.getId(),
//                "Booking of Artist",
//                payment.getReceivedAmount(),
//                "http://localhost:4200/user/artist/payment-callback",
//                "http://localhost:4200/"
//        ).doOnNext(paymentUrl -> {
//            String pidx = extractPidxFromUrl(paymentUrl); // Extract pidx from the URL
//            payment.setPidx(pidx); // Save the extracted pidx
//            paymentRepository.save(payment);
//            log.info("Updated payment with pidx: {}", payment.getPidx());
//        });
//    }
//
    private Mono<String> initiateKhaltiPayment(Long purchaseOrderId, String purchaseOrderName, double amount, String returnUrl, String websiteUrl) {
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

    //
//
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



//    @Transactional
//    @Override
//    public ResponseEntity<String> handleCallback(String pidx, String status, double amount, double totalAmount) {
//        try {
//            log.info("Processing callback with pidx={}, status={}, amount={}, totalAmount={}", pidx, status, amount, totalAmount);
//
//            Payment payment = paymentRepository.findByPidx(pidx).orElseThrow(
//                    () -> new EntityNotFoundException("Payment not found for pidx=" + pidx)
//            );
//
//            Booking booking = payment.getBookingId();
//
//            if ("Completed".equalsIgnoreCase(status)) {
//                log.info("Payment verified successfully for pidx={}", pidx);
//                payment.setPaymentStatus(PaymentStatus.COMPLETED);
//                payment.setReceivedAmount(amount);
//                paymentRepository.save(payment);
//
//                booking.setStatus(BookingStatus.CONFIRMED);
//                bookingRepository.save(booking);
//
//
//                VirtualCoinRequest virtualCoinRequest = new VirtualCoinRequest();
//                virtualCoinRequest.setArtistId(booking.getArtistId().getId());
//                if (booking.getPaymentType() == PaymentType.FULL) {
//                    virtualCoinRequest.setBalance(booking.getTotalAmount() - booking.getSystemCharges());
//                } else {
//                    virtualCoinRequest.setBalance(booking.getTotalAmount() / 2 - booking.getSystemCharges());
//                }
//
//                // Creating or Updating virtual coin
//                virtualCoinService.createOrUpdateVirtualCoin(virtualCoinRequest);
//
//                TransactionRequest transactionRequest = new TransactionRequest();
//                transactionRequest.setBookingId(booking.getId());
//                transactionRequest.setArtistId(booking.getArtistId().getId());
//                if (booking.getPaymentType() == PaymentType.FULL) {
//                    transactionRequest.setAmount(booking.getTotalAmount() - booking.getSystemCharges());
//                } else {
//                    transactionRequest.setAmount(booking.getTotalAmount() / 2 - booking.getSystemCharges());
//                }
//                transactionRequest.setTransactionPurpose(TransactionPurpose.BOOKING_PAYMENT);
//                transactionRequest.setTransactionType(TransactionType.CREDIT);
//                transactionRequest.setStatus(Status.APPROVED);
//                log.info("TransactionRequest before saving: {}", transactionRequest);
//                transactionService.createTransaction(transactionRequest);
//
//                // Send confirmation email
//                UserEntity user = booking.getUserId();
//                mailService.sendPaymentConfirmationEmail(user, booking, payment);
//
//                log.info("Payment and booking status updated successfully.");
//                return ResponseEntity.ok("Payment processed successfully");
//            } else {
//                log.info("Payment verification failed for pidx={}", pidx);
//                payment.setPaymentStatus(PaymentStatus.FAILED);
//                paymentRepository.save(payment);
//
//                booking.setStatus(BookingStatus.CANCELLED);
//                bookingRepository.save(booking);
//
//                // Send failure email
//                UserEntity user = booking.getUserId();
//                mailService.sendPaymentFailureEmail(user, booking, payment);
//
//                log.info("Payment failed and booking status updated to CANCELLED.");
//                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payment verification failed");
//            }
//        } catch (Exception e) {
//            log.error("Error occurred while processing callback: {}", e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error occurred");
//        }
//    }

//    @Override
//    public Page<BookingResponse> getAllBookingsOfUsers(Pageable pageable) {
//        log.info("Fetching all bookings for the logged-in user");
//
//        UserEntity loggedInUser = loggedInUserUtil.getLoggedInUser();
//
//        return bookingRepository.findAllByUserId(loggedInUser, pageable)
//                .map(this::toBookingResponse); // Clean map
//    }
//
//    private BookingResponse toBookingResponse(Booking booking) {
//        Artist artist = booking.getArtistId();
//
//        ArtistResponses artistResponse = new ArtistResponses(
//                artist,
//                artist.getUser().getFullName(),
//                artist.getBio(),
//                FileUrlUtil.getFileUri(artist.getUser().getProfileImage()),
//                artist.getStageName()
//        );
//
//        double receivedAmount = booking.getPayments() != null
//                ? booking.getPayments().stream()
//                .mapToDouble(Payment::getReceivedAmount)
//                .sum()
//                : 0.0;
//
//        return new BookingResponse(
//                booking.getId(),
//                booking.getTotalAmount(),
//                receivedAmount,
//                booking.getRemainingAmount(),
//                booking.getStatus().name(),
//                booking.getEventType(),
//                booking.getEventDate(),
//                booking.getStartTime(),
//                booking.getEndTime(),
//                booking.getVenue(),
//                artistResponse
//        );
//    }
//
//
//    @Override
//    public Page<ArtistBookingResponse> getAllBookingsOfArtists(Pageable pageable) {
//        log.info("Fetching confirmed bookings for the logged-in artist");
//
//        Artist artist = artistRepository.findById(loggedInUserUtil.getLoggedInArtist().getId())
//                .orElseThrow(() -> new EntityNotFoundException("Artist not found for the logged-in user"));
//
//        Page<Booking> bookings = bookingRepository.findAllByArtistId(artist, pageable);
//
//        // Filter only confirmed bookings first, then collect and manually paginate
//        List<ArtistBookingResponse> confirmedBookings = bookings.getContent().stream()
//                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
//                .map(booking -> {
//                    UserEntity user = booking.getUserId();
//                    return new ArtistBookingResponse(
//                            booking.getId(),
//                            booking.getTotalAmount(),
//                            booking.getPayments().stream()
//                                    .mapToDouble(Payment::getReceivedAmount)
//                                    .sum(),
//                            booking.getRemainingAmount(),
//                            booking.getSystemCharges(),
//                            booking.getStatus().name(),
//                            booking.getVenue(),
//                            booking.getEventDate(),
//                            new UserResponse(
//                                    user.getId(),
//                                    user.getFullName(),
//                                    user.getEmailId(),
//                                    user.getPhoneNumber(),
//                                    FileUrlUtil.getFileUri(user.getProfileImage())
//                            )
//                    );
//                })
//                .toList();
//
//        return new PageImpl<>(confirmedBookings, pageable, confirmedBookings.size());
//    }
//
    @Transactional
    @Override
    public Mono<String> withdraw(WithDrawRequest withDrawRequest) {
        log.info("Processing withdrawal request: {}", withDrawRequest);

        // Fetch the transaction by ID
        Transaction transaction = transactionRepository.findById(withDrawRequest.getTransactionId())
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with ID: " + withDrawRequest.getTransactionId()));

        // Validate transaction details
        if (!transaction.getTransactionPurpose().equals(TransactionPurpose.WITHDRAWAL_REQUEST) ||
                !transaction.getTransactionType().equals(TransactionType.DEBIT)) {
            throw new IllegalArgumentException("Invalid transaction type or purpose for withdrawal.");
        }

        // Extract required details
        Long artistId = transaction.getVirtualCoin().getArtist().getId();
        double amount = transaction.getAmount();
        Long virtualCoinId = transaction.getVirtualCoin().getVirtualCoinId();

        log.info("Artist ID: {}, Amount: {}, Virtual Coin ID: {}", artistId, amount, virtualCoinId);

        // Create a new Payment entity
        Payment payment = new Payment();
        payment.setReceivedAmount(amount);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setTransactionCode(transaction.getTransactionId().toString());
        payment.setUserInfoEntity(transaction.getVirtualCoin().getArtist().getUser());

        // Call Khalti payment initiation method
        return initiateKhaltiPayment(
                artistId, // Use artist ID as purchaseOrderId
                "Withdrawal Request",
                amount,
                "http://localhost:4200/admin/withdraw/callback",
                "http://localhost:4200/"
        ).doOnNext(paymentUrl -> {
            String pidx = extractPidxFromUrl(paymentUrl); // Extract pidx from the URL
            payment.setPidx(pidx); // Save the extracted pidx
            paymentRepository.save(payment);
            log.info("Updated payment with pidx: {}", payment.getPidx());
        });
    }

    @Transactional
    @Override
    public ResponseEntity<String> handleWithdrawCallBack(String pidx, String status, double amount) {
        try {
            log.info("Processing withdrawal callback with pidx={}, status={}, amount={}", pidx, status, amount);

            // Fetch the payment by pidx
            Payment payment = paymentRepository.findByPidx(pidx).orElseThrow(
                    () -> new EntityNotFoundException("Payment not found for pidx=" + pidx)
            );

            // Fetch the transaction using the transactionId stored in transaction_code
            Transaction transaction = transactionRepository.findById(Long.parseLong(payment.getTransactionCode()))
                    .orElseThrow(() -> new EntityNotFoundException("Transaction not found for ID=" + payment.getTransactionCode()));


            Artist artist = transaction.getVirtualCoin().getArtist();
            if ("Completed".equalsIgnoreCase(status)) {
                // Update payment status
                payment.setPaymentStatus(PaymentStatus.COMPLETED);
                paymentRepository.save(payment);

                // Update transaction status
                transaction.setStatus(Status.APPROVED);
                transactionRepository.save(transaction);

                // Update virtual coin balance
                VirtualCoin virtualCoin = transaction.getVirtualCoin();
                double newBalance = virtualCoin.getBalance() - transaction.getAmount();
                virtualCoin.setBalance(newBalance);
                virtualCoinRepository.save(virtualCoin);

                mailService.sendWithdrawalConfirmationEmail(
                        artist.getUser().getFullName(),
                        artist.getUser().getEmailId(),
                        amount,
                        transaction.getTransactionId()
                );

                return ResponseEntity.ok("Withdrawal payment processed successfully");
            } else {
                log.info("Withdrawal failed for pidx={}", pidx);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Withdrawal failed");
            }
        } catch (Exception e) {
            log.error("Error occurred while processing withdrawal callback: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error occurred");
        }
    }
}

