package com.brogrammers.open_mic_hub_service.security.jwt_auth;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decrypts an access token and verifies that we issued it.
 *
 * <p>Tokens are signed then encrypted. Decryption alone is not authentication — the encryption key
 * is the public key, so anyone can produce a well-formed JWE. Only the signature check, against our
 * private key's public half, establishes that the claims came from this service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenDecoder {

    private final RSAKeyRecord rsaKeyRecord;

    /**
     * @return the verified claims
     * @throws InvalidAccessTokenException if the token cannot be decrypted, carries no signature,
     *                                     or the signature does not verify
     */
    public JWTClaimsSet decodeAndVerify(String token) {
        try {
            JWEObject jweObject = JWEObject.parse(token);
            jweObject.decrypt(new RSADecrypter(rsaKeyRecord.rsaPrivateKey()));

            SignedJWT signedJWT = jweObject.getPayload().toSignedJWT();
            if (signedJWT == null) {
                throw new InvalidAccessTokenException("Token is not signed.");
            }
            if (!signedJWT.verify(new RSASSAVerifier(rsaKeyRecord.rsaPublicKey()))) {
                throw new InvalidAccessTokenException("Token signature does not verify.");
            }
            return signedJWT.getJWTClaimsSet();
        } catch (InvalidAccessTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccessTokenException("Token could not be read: " + e.getMessage());
        }
    }

    /** Verifies a token but answers with a flag instead of throwing. */
    public JWTClaimsSet decodeQuietly(String token) {
        try {
            return decodeAndVerify(token);
        } catch (InvalidAccessTokenException e) {
            log.warn("Rejected access token: {}", e.getMessage());
            return null;
        }
    }

    public static class InvalidAccessTokenException extends RuntimeException {
        public InvalidAccessTokenException(String message) {
            super(message);
        }
    }
}
