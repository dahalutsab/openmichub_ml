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
import com.brogrammers.open_mic_hub_service.payment.gateway.KhaltiClient;
import com.brogrammers.open_mic_hub_service.payment.gateway.KhaltiLookupResponse;
import com.brogrammers.open_mic_hub_service.payment.entity.PaymentStatus;
import com.brogrammers.open_mic_hub_service.payment.repository.PaymentRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository.ArtistAvailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.repository.ArtistUnavailabilityRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final ArtistRepository artistRepository;
    private final ArtistAvailabilityRepository artistAvailabilityRepository;
    private final ArtistUnavailabilityRepository artistUnavailabilityRepository;
    private final MailService mailService;
    private final KhaltiClient khaltiClient;

    @Value("${frontend.domain}")
    private String frontendDomain;
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

        // Blackout dates. The unavailability feature was fully built - entity, repository,
        // controller - and the booking path never consulted it, so artists could mark themselves
        // unavailable and still be booked.
        if (artistUnavailabilityRepository.existsByArtistAndDate(artist, bookingRequest.getEventDate())) {
            throw new IllegalArgumentException("Artist is unavailable on " + bookingRequest.getEventDate() + ".");
        }

        // Nothing previously stopped the same artist being booked by several organizers for the
        // same slot.
        boolean clashes = bookingRepository.existsOverlapping(
                artist, bookingRequest.getEventDate(), startTime, endTime,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
        if (clashes) {
            throw new IllegalArgumentException("Artist is already booked during that time.");
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

    /**
     * Loads a booking and confirms it belongs to the logged-in artist.
     *
     * <p>approve and decline previously took only an id, so any authenticated caller could accept
     * or reject any booking on the platform.
     */
    private Booking requireOwnBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        Artist loggedInArtist = loggedInUserUtil.getLoggedInArtist();
        if (!booking.getArtistId().getId().equals(loggedInArtist.getId())) {
            throw new AccessDeniedException("This booking was not made with you.");
        }
        return booking;
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

    /** Only the artist the booking was made with may approve it. */
    @Override
    public void approveBooking(Long bookingId) {
        Booking booking = requireOwnBooking(bookingId);

        Artist artist = booking.getArtistId();
        UserEntity user = booking.getUserId();

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        BookingServiceImpl.log.info("Booking approved: {}", booking);

        // Send approval email
        mailService.sendBookingApprovalEmail(user, artist, booking);
    }

    /** Only the artist the booking was made with may decline it. */
    @Override
    public void declineBooking(Long bookingId) {
        Booking booking = requireOwnBooking(bookingId);

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
    /**
     * Disburses an artist's pending withdrawal request through Khalti.
     *
     * <p>Restricted to admins: this moves real money out. The endpoint was previously public and
     * accepted any transaction id, so anyone could trigger a payout against a sequential id without
     * authenticating. The funds are already reserved by
     * {@code TransactionServiceImpl.withDraw}, so this only performs the disbursement.
     */
    @Override
    public String withdraw(WithDrawRequest withDrawRequest) {
        Transaction transaction = transactionRepository.findById(withDrawRequest.getTransactionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaction not found with ID: " + withDrawRequest.getTransactionId()));

        if (transaction.getTransactionPurpose() != TransactionPurpose.WITHDRAWAL_REQUEST
                || transaction.getTransactionType() != TransactionType.DEBIT) {
            throw new IllegalArgumentException("That transaction is not a withdrawal request.");
        }
        if (transaction.getStatus() != Status.PENDING) {
            throw new IllegalArgumentException(
                    "This withdrawal is already " + transaction.getStatus() + " and cannot be paid out again.");
        }

        Artist artist = transaction.getVirtualCoin().getArtist();
        double amount = transaction.getAmount();
        log.info("Disbursing withdrawal {} of {} to artist {}",
                transaction.getTransactionId(), amount, artist.getId());

        Payment payment = new Payment();
        payment.setReceivedAmount(amount);
        payment.setTotalAmount(amount);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod("KHALTI");
        payment.setProductCode("artist_withdrawal");
        payment.setTransactionCode(transaction.getTransactionId().toString());
        payment.setUserInfoEntity(artist.getUser());
        Payment saved = paymentRepository.save(payment);

        KhaltiInitiateResponse gateway = khaltiClient.initiate(
                artist.getId(),
                "Withdrawal Request",
                BigDecimal.valueOf(amount),
                frontendDomain + "/admin/withdraw/callback",
                frontendDomain + "/"
        );

        saved.setPidx(gateway.getPidx());
        paymentRepository.save(saved);
        return gateway.getPaymentUrl();
    }

    /**
     * Settles a withdrawal after Khalti reports back.
     *
     * <p>As with booking payments, the reported status is verified server-to-server rather than
     * trusted from the redirect. The balance is not touched on success — it was already reserved
     * when the artist raised the request. A failed payout returns the reserved funds.
     */
    @Transactional
    @Override
    public ResponseEntity<String> handleWithdrawCallBack(String pidx) {
        log.info("Processing withdrawal callback for pidx={}", pidx);

        Payment payment = paymentRepository.findByPidx(pidx).orElseThrow(
                () -> new EntityNotFoundException("Payment not found for pidx=" + pidx));

        Transaction transaction = transactionRepository.findById(Long.parseLong(payment.getTransactionCode()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaction not found for ID=" + payment.getTransactionCode()));

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            log.info("Withdrawal {} already settled; ignoring duplicate callback.", pidx);
            return ResponseEntity.ok("Withdrawal already processed");
        }

        KhaltiLookupResponse verified = khaltiClient.lookup(pidx);
        if (verified == null || !verified.isCompleted()) {
            log.warn("Khalti reports withdrawal pidx={} as '{}' - releasing hold.",
                    pidx, verified == null ? "no response" : verified.getStatus());
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            transactionService.releaseWithdrawalHold(transaction);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Withdrawal failed");
        }

        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(verified.getTransactionId());
        paymentRepository.save(payment);

        // Balance already reduced when the request was raised; only the status changes here.
        transaction.setStatus(Status.APPROVED);
        transactionRepository.save(transaction);

        Artist artist = transaction.getVirtualCoin().getArtist();
        mailService.sendWithdrawalConfirmationEmail(
                artist.getUser().getFullName(),
                artist.getUser().getEmailId(),
                transaction.getAmount(),
                transaction.getTransactionId());

        log.info("Withdrawal {} disbursed to artist {}.", pidx, artist.getId());
        return ResponseEntity.ok("Withdrawal payment processed successfully");
    }
}
