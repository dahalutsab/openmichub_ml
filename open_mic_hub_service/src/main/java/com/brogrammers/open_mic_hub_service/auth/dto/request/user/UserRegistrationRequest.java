package com.brogrammers.open_mic_hub_service.auth.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.multipart.MultipartFile;

public record UserRegistrationRequest(
        @NotEmpty(message = "Full name must not be empty")
        String fullName,
        @NotEmpty(message = "User recipientEmail must not be empty") // Neither null nor 0 size
        @Email(message = "Invalid recipientEmail format")
        String userEmail,
        @NotEmpty(message = "Password must not be empty")
        String password,
        MultipartFile profileImage,
        @NotEmpty(message = "Phone number must not be empty")
        String phoneNumber,
        @NotEmpty(message = "Location must not be empty")
        String location
) { }