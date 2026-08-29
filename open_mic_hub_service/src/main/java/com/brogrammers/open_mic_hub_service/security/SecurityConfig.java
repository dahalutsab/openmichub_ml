package com.brogrammers.open_mic_hub_service.security;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import com.brogrammers.open_mic_hub_service.security.jwt_auth.CustomExceptionHandlingFilter;
import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtAccessTokenFilter;
import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtTokenDecoder;
import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtTokenUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Security configuration class for securing the application with JWT-based authentication.
 * Configures WebSocket endpoints and integrates with existing JWT filters.
 *
 * @author Utsab Dahal
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RSAKeyRecord rsaKeyRecord;
    private final JwtTokenUtils jwtTokenUtils;
    private final JwtTokenDecoder jwtTokenDecoder;
    private final ObjectMapper objectMapper;

    /**
     * The application's chain.
     *
     * <p>Ordered after {@code OAuth2SecurityConfig}, which claims the two social sign-in handshake
     * paths. Everything else — every API call, carrying a bearer token — is matched here.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // Gateway callbacks only. Both verify the payment with Khalti server-to-server,
                    // so nothing on the request is trusted. /payments/booking and /artist/withdraw
                    // are deliberately NOT here - they initiate payments and require a caller.
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/payments/callback").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/artist/withdraw/callback").permitAll();
                    for (WHITE_LIST_URLS entry : WHITE_LIST_URLS.values()) {
                        for (HttpMethod method : entry.getMethods()) {
                            auth.requestMatchers(method, entry.getUrl()).permitAll();
                        }
                    }
                    // A single review by numeric id is public; /reviews and /reviews/me are not,
                    // so this is matched by shape rather than with a path wildcard.
                    auth.requestMatchers(RegexRequestMatcher.regexMatcher(
                            HttpMethod.GET, "/api/v1/reviews/\\d+")).permitAll();
                    auth.requestMatchers("/ws/**").permitAll(); // Ensure WebSocket bypasses HTTP filters
                    auth.anyRequest().authenticated();
                })
                // Move filters after permitAll evaluation
                .addFilterBefore(new CustomExceptionHandlingFilter(objectMapper), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAccessTokenFilter(rsaKeyRecord, jwtTokenUtils, jwtTokenDecoder), UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> SecurityContextHolder.clearContext())
                )
                .exceptionHandling(ex -> {
                    ex.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint());
                    ex.accessDeniedHandler(new BearerTokenAccessDeniedHandler());
                })
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}