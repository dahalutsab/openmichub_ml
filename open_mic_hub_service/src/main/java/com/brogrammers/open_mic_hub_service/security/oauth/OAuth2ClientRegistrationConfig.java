package com.brogrammers.open_mic_hub_service.security.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the social providers whose credentials are actually present.
 *
 * <p>Spring Boot can build these from {@code spring.security.oauth2.client.registration.*}, but it
 * validates that namespace on startup and refuses to boot on an empty client id. Declaring both
 * providers there with blank defaults therefore turns optional credentials into mandatory ones —
 * the application would not start until someone had registered a Google app. Reading our own keys
 * and constructing the registrations here keeps the feature genuinely optional, which is the same
 * contract payment, mail and the chat assistant already follow.
 *
 * <p>The class is skipped entirely when neither provider is configured, so no repository bean
 * exists and {@link OAuth2SecurityConfig} leaves the handshake paths closed.
 */
@Configuration
@ConditionalOnExpression(
        "!'${oauth.google.client-id:}'.isEmpty() or !'${oauth.facebook.client-id:}'.isEmpty()")
@Slf4j
public class OAuth2ClientRegistrationConfig {

    /**
     * Facebook's Graph API returns only {@code id} and {@code name} by default; every other field
     * this application reads has to be named in the request.
     */
    private static final String FACEBOOK_USER_INFO =
            "https://graph.facebook.com/me?fields=id,name,email,picture.type(large)";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${oauth.google.client-id:}") String googleClientId,
            @Value("${oauth.google.client-secret:}") String googleClientSecret,
            @Value("${oauth.facebook.client-id:}") String facebookClientId,
            @Value("${oauth.facebook.client-secret:}") String facebookClientSecret) {

        List<ClientRegistration> registrations = new ArrayList<>();

        if (configured(googleClientId, googleClientSecret)) {
            registrations.add(CommonOAuth2Provider.GOOGLE
                    .getBuilder("google")
                    .clientId(googleClientId.trim())
                    .clientSecret(googleClientSecret.trim())
                    .build());
            log.info("Google sign-in registered");
        }

        if (configured(facebookClientId, facebookClientSecret)) {
            registrations.add(CommonOAuth2Provider.FACEBOOK
                    .getBuilder("facebook")
                    .clientId(facebookClientId.trim())
                    .clientSecret(facebookClientSecret.trim())
                    .scope("public_profile", "email")
                    .userInfoUri(FACEBOOK_USER_INFO)
                    .build());
            log.info("Facebook sign-in registered");
        }

        if (registrations.isEmpty()) {
            // Reachable when a client id was given without its secret: the condition on this class
            // sees the id and admits it, and then neither provider is complete. Failing here is
            // right - a half-configured provider is a mistake, and silently offering a button that
            // cannot work is worse than not starting.
            throw new IllegalStateException(
                    "A social sign-in client id was set without its matching client secret. "
                            + "Set both, or clear both to turn the provider off.");
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    private boolean configured(String clientId, String clientSecret) {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
