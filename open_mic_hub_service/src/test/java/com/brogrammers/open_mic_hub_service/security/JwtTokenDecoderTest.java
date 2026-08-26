package com.brogrammers.open_mic_hub_service.security;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtTokenDecoder;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Access tokens are signed then encrypted.
 *
 * <p>They used to be encrypted only. Encryption is done with the <em>public</em> key, so anyone who
 * had it — and it ships in the repository — could encrypt claims naming any subject, and the server
 * would decrypt them and authenticate that user. These tests pin the signature requirement.
 */
class JwtTokenDecoderTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static RSAPublicKey attackerPublicKey;
    private static RSAPrivateKey attackerPrivateKey;

    private static JwtTokenDecoder decoder;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair server = generator.generateKeyPair();
        publicKey = (RSAPublicKey) server.getPublic();
        privateKey = (RSAPrivateKey) server.getPrivate();

        KeyPair attacker = generator.generateKeyPair();
        attackerPublicKey = (RSAPublicKey) attacker.getPublic();
        attackerPrivateKey = (RSAPrivateKey) attacker.getPrivate();

        decoder = new JwtTokenDecoder(new RSAKeyRecord(publicKey, privateKey));
    }

    private static JWTClaimsSet claimsFor(String subject) {
        return new JWTClaimsSet.Builder().issuer("open_mic_hub").subject(subject).build();
    }

    private static String encryptOnly(JWTClaimsSet claims) throws Exception {
        JWEObject jwe = new JWEObject(
                new JWEHeader(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM),
                new Payload(claims.toJSONObject()));
        jwe.encrypt(new RSAEncrypter(publicKey));
        return jwe.serialize();
    }

    private static String signWithThenEncrypt(RSAPrivateKey signingKey, JWTClaimsSet claims) throws Exception {
        SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        signed.sign(new RSASSASigner(signingKey));

        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                        .contentType("JWT").build(),
                new Payload(signed));
        jwe.encrypt(new RSAEncrypter(publicKey));
        return jwe.serialize();
    }

    @Test
    @DisplayName("a token we issued is accepted")
    void acceptsOurOwnToken() throws Exception {
        String token = signWithThenEncrypt(privateKey, claimsFor("someone@example.com"));

        JWTClaimsSet claims = decoder.decodeAndVerify(token);

        assertThat(claims.getSubject()).isEqualTo("someone@example.com");
    }

    @Test
    @DisplayName("an encrypt-only token forged with the public key is rejected")
    void rejectsEncryptOnlyForgery() throws Exception {
        // Exactly what the old generator produced, and what any holder of the public key could make.
        String forged = encryptOnly(claimsFor("admin@openmichub.com"));

        assertThatThrownBy(() -> decoder.decodeAndVerify(forged))
                .isInstanceOf(JwtTokenDecoder.InvalidAccessTokenException.class)
                .hasMessageContaining("not signed");
    }

    @Test
    @DisplayName("a token signed with someone else's key is rejected")
    void rejectsForeignSignature() throws Exception {
        String forged = signWithThenEncrypt(attackerPrivateKey, claimsFor("admin@openmichub.com"));

        assertThatThrownBy(() -> decoder.decodeAndVerify(forged))
                .isInstanceOf(JwtTokenDecoder.InvalidAccessTokenException.class)
                .hasMessageContaining("signature does not verify");
    }

    @Test
    @DisplayName("garbage is rejected rather than throwing something unexpected")
    void rejectsGarbage() {
        assertThatThrownBy(() -> decoder.decodeAndVerify("not-a-token"))
                .isInstanceOf(JwtTokenDecoder.InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("decodeQuietly answers null instead of throwing")
    void quietDecodeReturnsNull() throws Exception {
        assertThat(decoder.decodeQuietly(encryptOnly(claimsFor("admin@openmichub.com")))).isNull();
        assertThat(attackerPublicKey).isNotNull();
    }
}
