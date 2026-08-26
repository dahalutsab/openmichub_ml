package com.brogrammers.open_mic_hub_service.util.validator;

import java.util.regex.Pattern;

public class PhoneValidator {
    private PhoneValidator() {}

    // Regex pattern for Nepalese mobile and landline numbers
    private static final String PHONE_PATTERN =
            "^(?:\\+977[- ]?|0)?" +              // Optional country code (+977, +977-, 0)
                    "(?:" +
                    "(9[4-9]\\d{8})|" +                  // Mobile numbers: 10 digits (94xxxxxxxx, 96xxxxxxxx, 97xxxxxxxx, 98xxxxxxxx, 99xxxxxxxx)
                    "(01\\d{6,7})|" +                    // Kathmandu landline: 7-8 digits (01xxxxxx, 01xxxxxxx)
                    "([2-9][0-9]\\d{6})" +               // Other landlines: 8 digits (0XXxxxxxx, e.g., 021xxxxxx)
                    ")$";

    private static final Pattern pattern = Pattern.compile(PHONE_PATTERN);

    public static boolean isValid(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        // Remove any extra whitespace or hyphens for consistent matching
        String cleanedPhone = phone.trim().replaceAll("[\\s-]", "");
        return pattern.matcher(cleanedPhone).matches();
    }
}