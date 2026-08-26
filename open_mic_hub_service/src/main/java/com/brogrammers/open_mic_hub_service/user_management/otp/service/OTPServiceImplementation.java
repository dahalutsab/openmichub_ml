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

    @Override
    public OTP saveOTP(UserEntity user, OTPPurpose purpose) {
        OTP otp = OTP.builder()
                .user(user)
                .otpValue(otpUtil.generateOtp())
                .expiryTime(LocalDateTime.now().plusSeconds(expiryTime))
                .purpose(purpose)
                .build();
        return otpRepository.save(otp);
    }

    @Override
    public void validateOTP(UserEntity user, String otp, OTPPurpose purpose) {
        OTP otpEntity = otpRepository.findByUserAndOtpValueAndPurpose(user, otp, purpose)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTP"));
        if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired");
        }
        otpEntity.setIsUsed(true);
        otpRepository.save(otpEntity);
    }

    @Override
    public OTP getOTP(String otp, OTPPurpose purpose) {
        OTP userOTP = otpRepository.findByOtpValueAndPurpose(otp, purpose)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTP"));
        if (userOTP.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired");
        }
        return userOTP;
    }

}
