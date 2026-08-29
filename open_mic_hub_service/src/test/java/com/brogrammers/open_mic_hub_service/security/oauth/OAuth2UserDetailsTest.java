package com.brogrammers.open_mic_hub_service.security.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Google and Facebook describe the same person with different keys, and getting that mapping wrong
 * is silent: a missing subject id turns every sign-in into a new account, and a missing
 * verification flag turns the account-linking guard into a rubber stamp.
 */
class OAuth2UserDetailsTest {

    private static OAuth2User user(Map<String, Object> attributes, String nameKey) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")), attributes, nameKey);
    }

    @Test
    @DisplayName("Google: OpenID Connect claims, with the verification flag it actually sends")
    void readsGoogleClaims() {
        OAuth2UserDetails details = OAuth2UserDetails.from(AuthProvider.GOOGLE, user(Map.of(
                "sub", "110169484474386276334",
                "email", "aastha@example.com",
                "email_verified", true,
                "name", "Aastha Sharma",
                "picture", "https://lh3.googleusercontent.com/a/abc123"), "sub"));

        assertThat(details.providerId()).isEqualTo("110169484474386276334");
        assertThat(details.email()).isEqualTo("aastha@example.com");
        assertThat(details.name()).isEqualTo("Aastha Sharma");
        assertThat(details.pictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/abc123");
        assertThat(details.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("Google: an unverified address is reported as unverified")
    void honoursGoogleUnverifiedEmail() {
        OAuth2UserDetails details = OAuth2UserDetails.from(AuthProvider.GOOGLE, user(Map.of(
                "sub", "1", "email", "a@example.com", "email_verified", false, "name", "A"), "sub"));

        assertThat(details.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Google: a missing verification claim is not treated as verified")
    void treatsAbsentGoogleFlagAsUnverified() {
        // Absent must not read as true, or the linking guard passes on a claim Google never made.
        OAuth2UserDetails details = OAuth2UserDetails.from(AuthProvider.GOOGLE, user(Map.of(
                "sub", "1", "email", "a@example.com", "name", "A"), "sub"));

        assertThat(details.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Facebook: Graph API fields, including the nested picture")
    void readsFacebookFields() {
        OAuth2UserDetails details = OAuth2UserDetails.from(AuthProvider.FACEBOOK, user(Map.of(
                "id", "10223344556677889",
                "email", "bibek@example.com",
                "name", "Bibek Subedi",
                "picture", Map.of("data", Map.of("url", "https://scontent.xx.fbcdn.net/v/pic.jpg"))),
                "id"));

        assertThat(details.providerId()).isEqualTo("10223344556677889");
        assertThat(details.email()).isEqualTo("bibek@example.com");
        assertThat(details.name()).isEqualTo("Bibek Subedi");
        assertThat(details.pictureUrl()).isEqualTo("https://scontent.xx.fbcdn.net/v/pic.jpg");
    }

    @Test
    @DisplayName("Facebook: a missing picture is null rather than an exception")
    void toleratesMissingFacebookPicture() {
        // The picture field is only returned when asked for, and the shape varies by API version.
        OAuth2UserDetails details = OAuth2UserDetails.from(AuthProvider.FACEBOOK,
                user(Map.of("id", "1", "email", "b@example.com", "name", "B"), "id"));

        assertThat(details.pictureUrl()).isNull();
    }

    @Test
    @DisplayName("Facebook: a picture object of an unexpected shape does not blow up")
    void toleratesUnexpectedFacebookPictureShape() {
        OAuth2UserDetails details = OAuth2UserDetails.from(AuthProvider.FACEBOOK, user(Map.of(
                "id", "1", "email", "b@example.com", "name", "B",
                "picture", Map.of("data", "not-an-object")), "id"));

        assertThat(details.pictureUrl()).isNull();
    }

    @Test
    @DisplayName("registration ids map to providers, and unknown ones are refused")
    void mapsRegistrationIds() {
        assertThat(AuthProvider.fromRegistrationId("google")).isEqualTo(AuthProvider.GOOGLE);
        assertThat(AuthProvider.fromRegistrationId("FACEBOOK")).isEqualTo(AuthProvider.FACEBOOK);

        // LOCAL is not something a provider can claim to be.
        assertThatThrownBy(() -> AuthProvider.fromRegistrationId("local"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthProvider.fromRegistrationId("github"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
