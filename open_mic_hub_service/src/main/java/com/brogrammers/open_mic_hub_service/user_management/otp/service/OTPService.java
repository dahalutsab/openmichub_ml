package com.brogrammers.open_mic_hub_service.user_management.otp.service;


import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTP;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTPPurpose;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;

public interface OTPService {
    OTP saveOTP(UserEntity user, OTPPurpose purpose);

    void validateOTP(UserEntity user, String otp, OTPPurpose purpose);

    OTP getOTP(String otp, OTPPurpose purpose);

    /** Marks a secret spent so it cannot be redeemed twice. */
    void consumeOTP(OTP otp);
}
