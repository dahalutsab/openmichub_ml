package com.brogrammers.open_mic_hub_service.user_management.otp.repository;

import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTP;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTPPurpose;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {
    Optional<OTP> findByUserAndOtpValue(UserEntity user, String otp);

    Optional<OTP> findByOtpValueAndPurpose(String otp, OTPPurpose purpose);

    Optional<OTP> findByUserAndOtpValueAndPurpose(UserEntity user, String otpValue, OTPPurpose purpose);

    List<OTP> findAllByUserAndPurposeAndIsUsedFalse(UserEntity user, OTPPurpose purpose);
}
