package com.brogrammers.open_mic_hub_service.mail;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTP;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.scheduling.annotation.Async;

import java.net.URI;
import java.time.LocalDateTime;

public interface MailService {

    @Async
    void sendForgotPasswordMail(UserEntity userEntity, String forgotPasswordUrl, LocalDateTime expiry);

    @Async
    void sendRegistrationMail(UserEntity userEntity, OTP otp, URI frontEndUri);

    @Async
    void sendPaymentConfirmationEmail(UserEntity user, Booking booking, Payment payment);

    @Async
    void sendPaymentFailureEmail(UserEntity user, Booking booking, Payment payment);

    @Async
    void sendWithdrawalConfirmationEmail(String artistName, String artistEmail, double amount, Long transactionId);

    @Async
    void sendBookingRequestEmail(Artist artist, Booking booking);

    @Async
    void sendBookingApprovalEmail(UserEntity user, Artist artist, Booking booking);
    @Async
    void sendBookingDeclineEmail(UserEntity user, Booking booking);
}
