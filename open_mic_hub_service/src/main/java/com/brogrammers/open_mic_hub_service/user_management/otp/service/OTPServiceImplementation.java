package com.brogrammers.open_mic_hub_service.user_management.otp.service;

import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTP;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTPPurpose;
import com.brogrammers.open_mic_hub_service.user_management.otp.repository.OTPRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.otp.OtpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class OTPServiceImplementation implements OTPService {

    private final OTPRepository otpRepository;
    private final OtpUtil otpUtil;

    @Value("${otp.expiryInSeconds}")
    private long expiryTime;

    /**
     * Issues a one-time secret.
     *
     * <p>Password reset gets a long random token because it is delivered as a link and is looked up
     * by value; registration keeps the short code, which the user types and which is always checked
     * against a known user.
     */
    @Override
    public OTP saveOTP(UserEntity user, OTPPurpose purpose) {
        // Any outstanding secret for the same purpose is spent, so an old link stops working.
        otpRepository.findAllByUserAndPurposeAndIsUsedFalse(user, purpose)
                .forEach(previous -> {
                    previous.setIsUsed(true);
                    otpRepository.save(previous);
                });

        String value = purpose == OTPPurpose.FORGOT_PASSWORD
                ? otpUtil.generateResetToken()
                : otpUtil.generateOtp();

        OTP otp = OTP.builder()
                .user(user)
                .otpValue(value)
                .expiryTime(LocalDateTime.now().plusSeconds(expiryTime))
                .purpose(purpose)
                .build();
        return otpRepository.save(otp);
    }

    /** Marks a secret spent so it cannot be redeemed twice. */
    @Override
    public void consumeOTP(OTP otp) {
        otp.setIsUsed(true);
        otpRepository.save(otp);
    }

    @Override
    public void validateOTP(UserEntity user, String otp, OTPPurpose purpose) {
        OTP otpEntity = otpRepository.findByUserAndOtpValueAndPurpose(user, otp, purpose)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTP"));
        assertUsable(otpEntity);
        consumeOTP(otpEntity);
    }

    @Override
    public OTP getOTP(String otp, OTPPurpose purpose) {
        OTP userOTP = otpRepository.findByOtpValueAndPurpose(otp, purpose)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
        assertUsable(userOTP);
        return userOTP;
    }

    /**
     * A secret is usable only once and only before it expires. {@code isUsed} was written but never
     * read, so a reset token stayed valid for its whole window however often it was redeemed.
     */
    private void assertUsable(OTP otp) {
        if (Boolean.TRUE.equals(otp.getIsUsed())) {
            throw new IllegalArgumentException("This token has already been used");
        }
        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This token has expired");
        }
    }

}
