package com.brogrammers.open_mic_hub_service.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Returns the browser to the sign-in page with something the person can act on.
 *
 * <p>The default handler renders a Spring error page, which for a single-page application means
 * the user lands outside the app entirely with no way back. The refusals raised during
 * provisioning — an unverified address, an email already held by another provider — are the ones
 * worth reading, so they are passed through; anything else is reported generically rather than
 * leaking provider or configuration detail into a URL.
 */
@Component
@Slf4j
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String GENERIC = "Sign-in failed. Please try again.";

    private final String redirectUri;

    public OAuth2LoginFailureHandler(@Value("${frontend.domain}") String frontendDomain,
                                     @Value("${frontend.oauth_redirect}") String oauthRedirectPath) {
        this.redirectUri = frontendDomain + oauthRedirectPath;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Social sign-in failed: {}", exception.getMessage());

        String message = GENERIC;
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String description = oauthException.getError().getDescription();
            // Only the messages this application wrote itself. A provider's own error description
            // is not something to render back to a person.
            if (description != null && !description.isBlank() && isOurs(oauthException.getError().getErrorCode())) {
                message = description;
            }
        }

        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        String target = UriComponentsBuilder.fromUriString(redirectUri)
                .fragment("error=" + URLEncoder.encode(message, StandardCharsets.UTF_8))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private boolean isOurs(String code) {
        return switch (code) {
            case "no_email", "no_subject", "email_unverified", "provider_conflict", "role_missing" -> true;
            default -> false;
        };
    }
}
