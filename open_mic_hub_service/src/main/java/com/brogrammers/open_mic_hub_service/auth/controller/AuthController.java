package com.brogrammers.open_mic_hub_service.auth.controller;

import com.brogrammers.open_mic_hub_service.auth.dto.request.artist.ArtistRegistrationRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.login.AuthRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.user.UserRegistrationRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.forgot_password.ResetPasswordRequest;
import com.brogrammers.open_mic_hub_service.auth.messages.AuthResponseMessages;
import com.brogrammers.open_mic_hub_service.auth.service.AuthService;
import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.brogrammers.open_mic_hub_service.security.ratelimit.RateLimitExceededException;
import com.brogrammers.open_mic_hub_service.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;


@RestController
@RequestMapping(API_BASE + "/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "APIs for user authentication and password management")
public class AuthController extends BaseController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    /** Online guessing budget for credential and token endpoints. */
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private void limit(String action, String subject, HttpServletRequest request) {
        String key = action + ":" + subject.toLowerCase() + ":" + request.getRemoteAddr();
        if (!rateLimiter.tryAcquire(key, MAX_ATTEMPTS, ATTEMPT_WINDOW)) {
            throw new RateLimitExceededException(
                    "Too many attempts. Please wait a few minutes and try again.");
        }
    }

    @Operation(
            summary = "User login",
            description = "Authenticates a user using recipientEmail and password, then returns JWT access and refresh tokens."
    )
    @PostMapping("/login")
    public ResponseEntity<GlobalApiResponse> authenticateUser(
            @RequestBody AuthRequest authentication, HttpServletResponse response,
            HttpServletRequest request) {

        limit("login", authentication.email(), request);
        log.info("[AuthController:authenticateUser] User: {} is trying to authenticate", authentication.email());
        return successResponse(authService.getJwtTokensAfterAuthentication(authentication, response),
                AuthResponseMessages.USER_AUTHENTICATED);
    }


    //register
    @Operation(
            summary = "User registration",
            description = "Registers a new user with the provided details."
    )
    @PostMapping("/register/user")
    public ResponseEntity<GlobalApiResponse> registerUser(
            @ModelAttribute UserRegistrationRequest registration) {

        log.info("[AuthController:registerUser] User: {} is trying to register", registration.userEmail());
        return successResponse(authService.registerUser(registration),
                AuthResponseMessages.USER_REGISTERED);
    }

    @Operation(
            summary = "Artist registration",
            description = "Registers a new artist with the provided details.")
    @PostMapping("/register/artist")
    public ResponseEntity<GlobalApiResponse> registerArtist(
            @ModelAttribute ArtistRegistrationRequest registration) {
        return successResponse(authService.registerArtist(registration),
                AuthResponseMessages.ARTIST_REGISTERED);
    }

    @PostMapping("/verify-email")
    @Operation(
            summary = "Verify email",
            description = "Verifies the user's email using the provided token and email."
    )
    public ResponseEntity<GlobalApiResponse> verifyEmail(
            @RequestParam
            @Parameter(description = "Email address of the user", required = true, example = "user@openmichub.com") String email,
            @RequestParam
            @Parameter(description = "Verification token sent to the user's email", required = true, example = "123456") String token,
            HttpServletRequest request) {
        limit("verify-email", email, request);
        return successResponse(authService.verifyEmail(email, token),
                AuthResponseMessages.EMAIL_VERIFIED);
    }

    //resend verification email
    @Operation(
            summary = "Resend verification email",
            description = "Resends the verification email to the user."
    )
    @PostMapping("/resend-verification-email")
    public ResponseEntity<GlobalApiResponse> resendVerificationEmail(
            @RequestParam
            @Parameter(description = "User's email address", required = true, example = "user@openmichub.com") String email,
            HttpServletRequest request) {
        limit("resend-verification", email, request);
        log.info("[AuthController:resendVerificationEmail] Resending verification email to: {}", email);
        return successResponse(authService.resendVerificationEmail(email),
                AuthResponseMessages.VERIFICATION_EMAIL_RESENT);
    }

    @Operation(
            summary = "Forgot password",
            description = "Sends a password reset link to the provided recipientEmail."
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<GlobalApiResponse> forgotPassword(
            @RequestParam
            @Parameter(description = "Email address of the user", required = true, example = "admin@openmichub.com") String email,
            HttpServletRequest request) {

        limit("forgot-password", email, request);
        return successResponse(authService.forgotPassword(email),
                AuthResponseMessages.PASSWORD_RESET_LINK_SENT);
    }

    @Operation(
            summary = "Reset password",
            description = "Resets the user's password using the token received via recipientEmail."
    )
    @PostMapping("/reset-password")
    public ResponseEntity<GlobalApiResponse> resetPassword(
            @RequestBody ResetPasswordRequest resetPasswordRequest, HttpServletRequest request) {
        limit("reset-password", request.getRemoteAddr(), request);
        return successResponse(authService.resetPassword(resetPasswordRequest),
                AuthResponseMessages.PASSWORD_RESET_SUCCESSFULLY);
    }
}
