package com.brogrammers.open_mic_hub_service.security.ratelimit;

/** Raised when a caller exceeds the allowed number of attempts for a sensitive operation. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
