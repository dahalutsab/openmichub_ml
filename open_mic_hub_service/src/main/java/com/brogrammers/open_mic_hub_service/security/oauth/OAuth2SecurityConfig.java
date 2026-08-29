package com.brogrammers.open_mic_hub_service.security.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * The social sign-in handshake, on its own filter chain.
 *
 * <p>It needs a chain of its own because the rest of the application is stateless and this part
 * cannot be. The authorization code flow leaves the browser, goes to Google or Facebook, and comes
 * back — and the {@code state} value that proves the returning request belongs to the one that
 * left has to be held somewhere in between. That is a session, and it lives only for the length of
 * the round trip: {@link OAuth2LoginSuccessHandler} invalidates it the moment a token has been
 * issued. Every request after that is a bearer token like any other.
 *
 * <p>These are confidential clients — the code is exchanged server-to-server with a client secret
 * the browser never sees — so {@code state} carries the protection here and Spring does not add a
 * PKCE challenge. PKCE would be defence in depth rather than a fix for a gap; add it with
 * {@code OAuth2AuthorizationRequestCustomizers.withPkce()} if a provider is ever moved to a public
 * client.
 *
 * <p>Ordered ahead of the main chain and scoped by {@code securityMatcher}, so it claims only the
 * two handshake paths and nothing else changes.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OAuth2SecurityConfig {

    /** Where the provider sends the browser, and where the browser is sent to begin. */
    static final String[] HANDSHAKE_PATHS = {"/oauth2/authorization/**", "/login/oauth2/code/**"};

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {

        http.securityMatcher(HANDSHAKE_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults());

        // Resolved here rather than with @ConditionalOnBean: this repository is contributed by
        // Spring Boot's own auto-configuration, which only registers it when a provider actually
        // has a client id. Asking for it at bean-creation time gets a reliable answer, where a
        // conditional on a user configuration class races the auto-configuration that defines it.
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();

        if (registrations == null) {
            // No provider configured. The stack still boots — the same contract payment, mail and
            // the chat assistant follow — and these paths simply do not answer.
            log.info("No social sign-in provider configured; Google and Facebook sign-in are off");
            return http
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                    .build();
        }

        log.info("Social sign-in enabled");
        return http
                // IF_REQUIRED, not ALWAYS: a session is created for the handshake and for nothing
                // else.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .clientRegistrationRepository(registrations)
                        // Both, deliberately. `userService` covers plain OAuth2 providers and
                        // `oidcUserService` covers those asking for the `openid` scope; Google is
                        // the second kind, so registering only the first meant its sign-ins never
                        // reached the account provisioning at all.
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .build();
    }
}
