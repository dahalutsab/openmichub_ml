package com.brogrammers.open_mic_hub_service.util.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class OtpUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;

    /**
     * Six-digit code for email verification, where the user types it in by hand.
     *
     * <p>Only suitable when the code is bound to a known user and attempts are limited. It must not
     * be used for password reset — see {@link #generateResetToken()}.
     */
    public String generateOtp() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    /**
     * Opaque 256-bit token for password-reset links.
     *
     * <p>Reset previously used the same six-digit code and looked it up by value alone, across all
     * users. That is a 900,000-value keyspace with no rate limiting, so an attacker could walk it
     * and reset whichever account happened to match.
     */
    public String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
