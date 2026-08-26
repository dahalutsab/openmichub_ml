package com.brogrammers.open_mic_hub_service.util.validator;

import java.util.regex.Pattern;

public class EmailValidator {
    private EmailValidator() {}

    private static final Pattern pattern = Pattern.compile(
            "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"
    );

    public static boolean isValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return pattern.matcher(email.trim()).matches();
    }
}
