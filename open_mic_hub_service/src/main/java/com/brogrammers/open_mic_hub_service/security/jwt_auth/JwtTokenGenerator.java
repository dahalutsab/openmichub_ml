package com.brogrammers.open_mic_hub_service.security.jwt_auth;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenGenerator {

    private final RSAKeyRecord rsaKeyRecord;

    public String generateAccessToken(Authentication authentication) {
        try {
            log.info("[JwtTokenGenerator:generateAccessToken] Token Creation Started for: {}", authentication.getName());

            String roles = getRolesOfUser(authentication);
            String permissions = getPermissionsFromRoles(roles);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("open_mic_hub")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plus(150, ChronoUnit.MINUTES)))
                    .subject(authentication.getName())
                    .claim("scope", permissions)
                    .build();

            // Sign first, then encrypt. Encryption alone proves only confidentiality: because the
            // public key is public, anyone could previously encrypt claims of their choosing and
            // the server would decrypt and trust them. The signature is what proves we issued it.
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            signedJWT.sign(new RSASSASigner(rsaKeyRecord.rsaPrivateKey()));

            JWEObject jweObject = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                            .contentType("JWT")
                            .build(),
                    new Payload(signedJWT)
            );

            jweObject.encrypt(new RSAEncrypter(rsaKeyRecord.rsaPublicKey()));

            String token = jweObject.serialize();
            log.info("[JwtTokenGenerator:generateAccessToken] Generated encrypted access token for: {}", authentication.getName());
            return token;
        } catch (JOSEException e) {
            log.error("[JwtTokenGenerator:generateAccessToken] Error generating token: {}", e.getMessage());
            throw new RuntimeException("Failed to generate access token", e);
        }
    }


    private static String getRolesOfUser(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));
    }

    /**
     * Coarse permissions derived from the caller's roles.
     *
     * <p>This used to look for {@code ROLE_SCHOOL_ADMIN}, a role that does not exist in this
     * application, so the scope claim came out empty for everyone.
     */
    private String getPermissionsFromRoles(String roles) {
        Set<String> permissions = new LinkedHashSet<>();
        if (roles.contains("ROLE_ADMIN")) {
            permissions.addAll(List.of("READ", "WRITE", "DELETE"));
        }
        if (roles.contains("ROLE_ARTIST")) {
            permissions.addAll(List.of("READ", "WRITE"));
        }
        if (roles.contains("ROLE_USER")) {
            permissions.add("READ");
        }
        return String.join(" ", permissions);
    }
}