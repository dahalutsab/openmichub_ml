package com.brogrammers.open_mic_hub_service.config.auditing;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class ApplicationAuditing {

    private static final String SYSTEM = "system";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return new AuditorAwareImpl();
    }

    public static class AuditorAwareImpl implements AuditorAware<String> {
        @Override
        public Optional<String> getCurrentAuditor() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(SYSTEM);
            }

            Object principal = authentication.getPrincipal();

            if (principal instanceof UserEntity userEntity) {
                return Optional.of(userEntity.getEmailId());
            } else if (principal instanceof UserDetails userDetails) {
                return Optional.of(userDetails.getUsername());
            } else if (principal instanceof Jwt jwt) {
                return Optional.of(jwt.getSubject());
            } else if (principal instanceof String && principal.equals("anonymousUser")) {
                return Optional.of(SYSTEM);
            }

            return Optional.of(SYSTEM);
        }
    }

}