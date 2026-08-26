package com.brogrammers.open_mic_hub_service.mail;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.payment.entity.Payment;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTP;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    @Async
    @Override
    public void sendForgotPasswordMail(UserEntity userEntity, String forgotPasswordUrl, LocalDateTime expiry) {
        String email = userEntity.getEmailId();
        String name = userEntity.getFullName();

        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("resetLink", forgotPasswordUrl);
        context.setVariable("expiryTime", expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        context.setVariable("email", email);

        String content = templateEngine.process("forgot-password-link.html", context);
        sendEmail(email, "Reset your password", content);
    }

    @Override
    public void sendRegistrationMail(UserEntity userEntity, OTP otp, URI frontEndUri) {
        String email = userEntity.getEmailId();
        String name = userEntity.getFullName();

        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("otp", otp.getOtpValue());
        context.setVariable("expiryTime", otp.getExpiryTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        context.setVariable("email", email);
        context.setVariable("frontEndUri", frontEndUri.toString());

        String content = templateEngine.process("registration-otp.html", context);
        sendEmail(email, "Verify your email address", content);
    }

    @Async
    @Override
    public void sendPaymentConfirmationEmail(UserEntity user, Booking booking, Payment payment) {
        try {
            Context context = new Context();
            context.setVariable("fullName", user.getFullName());
            context.setVariable("amountPaid", payment.getReceivedAmount()/100);
            context.setVariable("paymentMethod", payment.getPaymentMethod());
            context.setVariable("artistName", booking.getArtistId().getUser().getFullName());
            context.setVariable("eventDateTime", booking.getEventDate() + " " + booking.getStartTime());
            context.setVariable("bookingId", booking.getId());

            String content = templateEngine.process("payment-success.html", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(user.getEmailId());
            helper.setSubject("Payment Confirmation - Open Mic Hub");
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Payment confirmation email sent to {}", user.getEmailId());
        } catch (MessagingException e) {
            log.error("Error sending mail to {}: {}", user.getEmailId(), e.getMessage());
            throw new RuntimeException("Error sending mail", e);
        }
    }


    @Async
    public void sendPaymentFailureEmail(UserEntity user, Booking booking, Payment payment) {
        try {
            Context context = new Context();
            context.setVariable("fullName", user.getFullName());
            context.setVariable("amount", payment.getReceivedAmount()); // Convert to rupees
            context.setVariable("paymentMethod", payment.getPaymentMethod());
            context.setVariable("artistName", booking.getArtistId().getUser().getFullName());
            context.setVariable("eventDateTime", booking.getEventDate() + " " + booking.getStartTime());
            context.setVariable("bookingId", booking.getId());

            String content = templateEngine.process("payment-failure.html", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(user.getEmailId());
            helper.setSubject("Payment Failed - Open Mic Hub");
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Payment failure email sent to {}", user.getEmailId());
        } catch (MessagingException e) {
            log.error("Error sending payment failure email to {}: {}", user.getEmailId(), e.getMessage());
            throw new RuntimeException("Error sending payment failure email", e);
        }
    }



    private void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            javaMailSender.send(message);
        } catch (MessagingException e) {
            log.error("Error sending mail to {}: {}", to, e.getMessage());
            throw new RuntimeException("Error sending mail", e);
        }
    }

    @Async
    public void sendWithdrawalConfirmationEmail(String artistName, String artistEmail, double amount, Long transactionId) {
        try {
            Context context = new Context();
            context.setVariable("artistName", artistName);
            context.setVariable("amount", amount);
            context.setVariable("transactionId", transactionId);

            String content = templateEngine.process("withdrawl-success.html", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(artistEmail);
            helper.setSubject("Withdrawal Confirmation - Open Mic Hub");
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Withdrawal confirmation email sent to {}", artistEmail);
        } catch (MessagingException e) {
            log.error("Error sending withdrawal confirmation email to {}: {}", artistEmail, e.getMessage());
            throw new RuntimeException("Error sending withdrawal confirmation email", e);
        }
    }

    @Async
    public void sendBookingRequestEmail(Artist artist, Booking booking) {
        try {
            Context context = new Context();
            context.setVariable("artistName", artist.getUser().getFullName());
            context.setVariable("userName", booking.getUserId().getFullName());
            context.setVariable("eventDate", booking.getEventDate());
            context.setVariable("startTime", booking.getStartTime());
            context.setVariable("endTime", booking.getEndTime());
            context.setVariable("venue", booking.getVenue());
            context.setVariable("eventType", booking.getEventType());

            String content = templateEngine.process("booking-request.html", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(artist.getUser().getEmailId());
            helper.setSubject("New Booking Request - Open Mic Hub");
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Booking request email sent to {}", artist.getUser().getEmailId());
        } catch (MessagingException e) {
            log.error("Error sending booking request email to {}: {}", artist.getUser().getEmailId(), e.getMessage());
            throw new RuntimeException("Error sending booking request email", e);
        }
    }

    @Async
    @Override
    public void sendBookingApprovalEmail(UserEntity user, Artist artist, Booking booking) {
        try {
            Context context = new Context();
            context.setVariable("userName", user.getFullName());
            context.setVariable("artistName", artist.getUser().getFullName());
            context.setVariable("eventDate", booking.getEventDate());
            context.setVariable("startTime", booking.getStartTime());
            context.setVariable("endTime", booking.getEndTime());
            context.setVariable("venue", booking.getVenue());
            context.setVariable("eventType", booking.getEventType());

            String content = templateEngine.process("approve-mail.html", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(user.getEmailId());
            helper.setSubject("Booking Approved - Open Mic Hub");
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Booking approval email sent to {}", user.getEmailId());
        } catch (MessagingException e) {
            log.error("Error sending booking approval email to {}: {}", user.getEmailId(), e.getMessage());
            throw new RuntimeException("Error sending booking approval email", e);
        }
    }


    @Async
    @Override
    public void sendBookingDeclineEmail(UserEntity user, Booking booking) {
        try {
            Context context = new Context();
            context.setVariable("userName", user.getFullName());
            context.setVariable("artistName", booking.getArtistId().getUser().getFullName());

            String content = templateEngine.process("decline-mail.html", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("no-reply@openmichub.com");
            helper.setTo(user.getEmailId());
            helper.setSubject("Booking Declined - Open Mic Hub");
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Booking decline email sent to {}", user.getEmailId());
        } catch (MessagingException e) {
            log.error("Error sending booking decline email to {}: {}", user.getEmailId(), e.getMessage());
            throw new RuntimeException("Error sending booking decline email", e);
        }
    }



}
