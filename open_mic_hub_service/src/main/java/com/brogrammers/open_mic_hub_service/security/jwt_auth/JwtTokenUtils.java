package com.brogrammers.open_mic_hub_service.security.jwt_auth;

import com.brogrammers.open_mic_hub_service.config.user.UserInfoConfig;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenUtils {

    private final UserInfoRepository userInfoRepository;

    public String getUserName(JWTClaimsSet claims) {
        return claims.getSubject();
    }

    public boolean isTokenValid(JWTClaimsSet claims, UserDetails userDetails) {
        final String userName = getUserName(claims);
        boolean isTokenExpired = getIfTokenIsExpired(claims);
        boolean isTokenUserSameAsDatabase = userName.equals(userDetails.getUsername());
        return !isTokenExpired && isTokenUserSameAsDatabase;
    }

    private boolean getIfTokenIsExpired(JWTClaimsSet claims) {
        Date expiration = claims.getExpirationTime();
        return expiration != null && expiration.before(Date.from(Instant.now()));
    }

    public UserDetails userDetails(String emailId) {
        return userInfoRepository
                .findByEmailId(emailId)
                .map(UserInfoConfig::new)
                .orElseThrow(() -> new UsernameNotFoundException("UserEmail: " + emailId + " does not exist"));
    }
}