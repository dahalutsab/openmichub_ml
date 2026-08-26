package com.brogrammers.open_mic_hub_service.util.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpUtil {


    public String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // Internal class to store OTP data
    private static class OtpData {
        String otp;
        long expiryTime;

        OtpData(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }
}