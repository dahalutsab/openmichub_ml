package com.brogrammers.open_mic_hub_service.auth.service;

import com.brogrammers.open_mic_hub_service.auth.dto.request.artist.ArtistRegistrationRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.login.AuthRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.user.UserRegistrationRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.forgot_password.ResetPasswordRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.response.ArtistRegistrationResponse;
import com.brogrammers.open_mic_hub_service.auth.dto.response.AuthResponse;
import com.brogrammers.open_mic_hub_service.auth.dto.response.ForgotPasswordResponse;
import com.brogrammers.open_mic_hub_service.auth.dto.response.UserRegistrationResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {


    AuthResponse getJwtTokensAfterAuthentication(AuthRequest authenticationRequest, HttpServletResponse response);

    ForgotPasswordResponse forgotPassword(String email);

    String resetPassword(ResetPasswordRequest resetPasswordRequest);

    UserRegistrationResponse registerUser(UserRegistrationRequest registration);

    String verifyEmail(String email, String token);

    UserRegistrationResponse resendVerificationEmail(String email);

    ArtistRegistrationResponse registerArtist(ArtistRegistrationRequest registration);
}